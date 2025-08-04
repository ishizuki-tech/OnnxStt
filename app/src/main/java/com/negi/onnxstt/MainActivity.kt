package com.negi.onnxstt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var sttEngine: OnnxSttEngine
    private lateinit var audioRecorder: AudioRecorderManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sttEngine = OnnxSttEngine(application)
        audioRecorder = AudioRecorderManager()

        setContent {
            MainUi()
        }
    }

    override fun onStop() {
        super.onStop()
        audioRecorder.stopRecording()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorder.stopRecording()
        runCatching { sttEngine.release() }
            .onFailure { Log.w("MainActivity", "Recognizer close ignored", it) }
    }

    @Composable
    fun MainUi() {
        val context = LocalContext.current

        // パーミッション管理
        var hasMicPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            )
        }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> hasMicPermission = granted }

        // モデル初期化フラグ・UI状態
        var modelReady by remember { mutableStateOf(false) }
        var uiText by remember { mutableStateOf("") }
        var uiIsRecording by remember { mutableStateOf(false) }

        // パーミッションリクエスト
        LaunchedEffect(Unit) {
            if (!hasMicPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        // モデル初期化
        LaunchedEffect(hasMicPermission) {
            if (hasMicPermission && !modelReady) {
                runCatching {
                    sttEngine.initModel()
                    modelReady = true
                }.onFailure { Log.e("MainActivity", "Failed to initialize model", it) }
            }
        }

        // UI
        TranscriptionScreen(
            hasPermission = hasMicPermission,
            modelReady = modelReady,
            isRecording = uiIsRecording,
            text = uiText,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            onToggle = {
                if (uiIsRecording) {
                    audioRecorder.stopRecording()
                    uiIsRecording = false
                } else {
                    if (!audioRecorder.initMicrophone(context)) return@TranscriptionScreen
                    audioRecorder.startRecording(
                        sttEngine = sttEngine,
                        onText = { uiText = it }
                    )
                    uiIsRecording = true
                }
            }
        )
    }
}
