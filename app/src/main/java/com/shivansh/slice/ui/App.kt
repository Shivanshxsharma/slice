package com.shivansh.slice.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.shivansh.slice.SliceViewModel
import com.shivansh.slice.Stage
import com.shivansh.slice.split.SavedPart

@Composable
fun SliceApp(vm: SliceViewModel) {
    val c = LocalColors.current
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.load(uri)
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }

    when (vm.stage) {
        Stage.Empty -> Unit
        Stage.Reading, Stage.Setup -> BackHandler { vm.reset() }
        is Stage.Splitting -> BackHandler { vm.cancelSplit() }
        is Stage.Done -> BackHandler { vm.reset() }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        AnimatedContent(
            targetState = vm.stage,
            contentKey = { it::class },
            transitionSpec = {
                (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.98f)) togetherWith fadeOut(tween(160))
            },
            label = "stage",
        ) { stage ->
            when (stage) {
                Stage.Empty -> EmptyScreen(onPick = pick)
                Stage.Reading -> ReadingScreen()
                Stage.Setup -> SetupScreen(vm, onClose = vm::reset)
                is Stage.Splitting -> {
                    KeepScreenOn()
                    SplittingScreen(vm.info?.name ?: "", (vm.stage as? Stage.Splitting)?.progress ?: stage.progress, onCancel = vm::cancelSplit)
                }
                is Stage.Done -> {
                    val result = (vm.stage as? Stage.Done)?.result ?: stage.result
                    DoneScreen(
                        vm, result,
                        onPlay = { play(context, it) },
                        onShare = { share(context, listOf(it)) },
                        onShareAll = { share(context, result.parts) },
                        onAgain = vm::backToSetup,
                        onNew = { vm.reset(); pick() },
                    )
                }
            }
        }
    }

    vm.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.error = null },
            confirmButton = { TextButton(onClick = { vm.error = null }) { Text("OK", color = c.accent) } },
            text = { Text(msg, style = Type.body, color = c.text) },
            containerColor = c.surface,
        )
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

private fun mimeOf(p: SavedPart) = if (p.name.endsWith(".webm", ignoreCase = true)) "video/webm" else "video/mp4"

private fun play(context: Context, p: SavedPart) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(p.uri, mimeOf(p)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No video player found", Toast.LENGTH_SHORT).show()
    }
}

private fun share(context: Context, parts: List<SavedPart>) {
    if (parts.isEmpty()) return
    val uris = ArrayList<Uri>(parts.map { it.uri })
    val type = parts.map(::mimeOf).distinct().singleOrNull() ?: "video/*"
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).setType(type).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    // ClipData carries the read grant for every URI through the chooser.
    val clip = ClipData.newRawUri(parts[0].name, uris[0])
    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
    intent.clipData = clip
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, if (uris.size == 1) "Share part" else "Share ${uris.size} parts"))
}
