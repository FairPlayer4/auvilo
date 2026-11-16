package com.avlreminder.alarm

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

/**
 * Camera preview and shutter; the photo goes to filesDir/proofs/, never to the gallery.
 * [keys]: the volume keys release the shutter while the preview is on screen (N2a).
 */
@Composable
fun ProofCamera(occurrenceId: Long, keys: ShutterKeys? = null, onPhoto: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var allowed by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    if (!allowed) {
        Button(onClick = { ask.launch(Manifest.permission.CAMERA) }, Modifier.fillMaxWidth()) { Text("Kamera erlauben") }
        return
    }

    val controller = remember { LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) } }
    DisposableEffect(owner) {
        controller.bindToLifecycle(owner)
        onDispose { controller.unbind() }
    }
    var busy by remember { mutableStateOf(false) }
    fun take() {
        if (busy) return
        busy = true
        val file = File(context.filesDir, "proofs/occ-$occurrenceId-${System.currentTimeMillis()}.jpg")
        file.parentFile?.mkdirs()
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = onPhoto(file.path)
                override fun onError(e: ImageCaptureException) {
                    busy = false
                    Toast.makeText(context, "Foto fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
        )
    }
    val currentTake by rememberUpdatedState(::take)
    if (keys != null) {
        DisposableEffect(keys) {
            keys.shutter = { currentTake() }
            onDispose { keys.shutter = null }
        }
    }
    AndroidView(
        factory = { PreviewView(it).apply { this.controller = controller } },
        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(16.dp)),
    )
    Button(
        onClick = ::take,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp),
    ) { Text(if (busy) "Speichere …" else "Foto aufnehmen") }
}
