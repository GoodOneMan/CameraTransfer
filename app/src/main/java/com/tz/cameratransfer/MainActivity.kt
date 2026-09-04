package com.tz.cameratransfer

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.tz.cameratransfer.network.NetworkClient
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf("camera") }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }

    when (currentScreen) {
        "camera" -> CameraScreen(
            onImageCaptured = { uri ->
                capturedImageUri = uri
                currentScreen = "preview"
            },
            onOpenSettings = { currentScreen = "settings" }
        )
        "preview" -> PreviewScreen(
            imageUri = capturedImageUri,
            onSend = { success ->
                if (success) {
                    currentScreen = "camera"
                    capturedImageUri = null
                }
            },
            onRetake = {
                currentScreen = "camera"
                capturedImageUri = null
            }
        )
        "settings" -> SettingsScreen(onBack = { currentScreen = "camera" })
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(onImageCaptured: (Uri) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (cameraPermissionState.status.isGranted) {
        Column(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        imageCapture = ImageCapture.Builder().build()
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onOpenSettings) { Text("Настройки") }
                Button(onClick = {
                    val executor = Executors.newSingleThreadExecutor()
                    val photoFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    imageCapture?.takePicture(
                        outputOptions, executor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                onImageCaptured(output.savedUri ?: Uri.fromFile(photoFile))
                            }
                            override fun onError(exc: ImageCaptureException) {
                                Toast.makeText(context, "Ошибка камеры: ${exc.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }) { Text("Сделать фото") }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Требуется разрешение на использование камеры")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                    Text("Предоставить")
                }
            }
        }
    }
}

@Composable
fun PreviewScreen(imageUri: Uri?, onSend: (Boolean) -> Unit, onRetake: () -> Unit) {
    val context = LocalContext.current
    var comment by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (imageUri != null) {
            Image(
                painter = rememberAsyncImagePainter(imageUri),
                contentDescription = "Preview",
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            label = { Text("Комментарий") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onRetake, enabled = !isLoading) { Text("Переснять") }
            Button(onClick = {
                isLoading = true
                scope.launch {
                    try {
                        //val settings = SettingsManager.getSettings(context).value
                        //val baseUrl = "http://${settings.first}:${settings.second}/"
                        val (ip, port) = SettingsManager.getSettings(context).first()
                        val baseUrl = "http://${ip}:${port}/"
                        val api = NetworkClient.getApiService(baseUrl)

                        val compressedFile = NetworkClient.compressImage(context, imageUri!!)
                        val requestFile = compressedFile.asRequestBody("image/jpeg".toMediaType())
                        val body = MultipartBody.Part.createFormData("image", compressedFile.name, requestFile)
                        val commentBody = comment.toRequestBody("text/plain".toMediaType())

                        val response = api.sendPhoto(body, commentBody)
                        if (response.isSuccessful) {
                            Toast.makeText(context, "Успешно отправлено!", Toast.LENGTH_SHORT).show()
                            onSend(true)
                        } else {
                            Toast.makeText(context, "Ошибка сервера: ${response.code()}", Toast.LENGTH_LONG).show()
                            isLoading = false
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Ошибка сети: ${e.message}", Toast.LENGTH_LONG).show()
                        isLoading = false
                    }
                }
            }, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Отправить")
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var ip by remember { mutableStateOf("192.168.1.100") }
    var port by remember { mutableStateOf("8080") }
    val scope = rememberCoroutineScope()

    // Загрузка текущих настроек
    LaunchedEffect(Unit) {
        SettingsManager.getSettings(context).collect { settings ->
            ip = settings.first
            port = settings.second
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Настройки подключения", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))

        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text("IP-адрес компьютера") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
        )

        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Порт") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Button(onClick = {
            scope.launch {
                SettingsManager.saveSettings(context, ip, port)
                onBack()
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Сохранить")
        }

        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) {
            Text("Назад")
        }
    }
}