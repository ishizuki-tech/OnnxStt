//package com.negi.onnxstt
//
//import android.Manifest
//import android.content.pm.PackageManager
//import android.media.AudioFormat
//import android.media.AudioRecord
//import android.media.MediaRecorder
//import android.os.Build
//import android.os.Bundle
//import android.util.Log
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.rememberLauncherForActivityResult
//import androidx.activity.compose.setContent
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.rememberScrollState
//import androidx.compose.foundation.verticalScroll
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.text.style.TextOverflow
//import androidx.compose.ui.unit.dp
//import androidx.core.content.ContextCompat
//import com.k2fsa.sherpa.onnx.*
//import java.io.File
//import java.io.FileOutputStream
//import java.io.IOException
//import kotlin.concurrent.thread
//
///**
// * sherpa-onnx + ONNX Runtime を使ったストリーミング音声認識サンプル Activity。
// *
// * 主要な責務:
// *  - UI制御（録音・結果表示）: Jetpack Compose
// *  - 音声入出力: AudioRecord
// *  - 認識処理: バックグラウンドスレッドでデコード&UI反映
// */
//class MainActivity : ComponentActivity() {
//
//    companion object {
//        private const val TAG = "sherpa-onnx"
//
//        // 録音関連設定
//        private const val SAMPLE_RATE_HZ = 16_000
//        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
//        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
//        private const val CHUNK_SECONDS = 0.1f
//        private const val PARAFORMER_TAIL_SECONDS = 0.8f
//    }
//
//    // sherpa-onnx インスタンス
//    private lateinit var recognizer: OnlineRecognizer
//
//    // AudioRecord 関連
//    private var audioRecord: AudioRecord? = null
//    private var recordingThread: Thread? = null
//    @Volatile private var isRecording = false
//
//    // テキストUI関連
//    private var segmentIndex = 0
//    private var committedText = ""   // 確定分
//    private var lastUiRendered = ""  // UI反映済みテキスト
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        setContent {
//            MaterialTheme {
//                val context = LocalContext.current
//
//                // ===== マイク権限管理 =====
//                var hasMicPermission by remember {
//                    mutableStateOf(
//                        ContextCompat.checkSelfPermission(
//                            context, Manifest.permission.RECORD_AUDIO
//                        ) == PackageManager.PERMISSION_GRANTED
//                    )
//                }
//                val permissionLauncher = rememberLauncherForActivityResult(
//                    ActivityResultContracts.RequestPermission()
//                ) { granted -> hasMicPermission = granted }
//
//                // ===== モデル初期化・状態管理 =====
//                var modelReady by remember { mutableStateOf(false) }
//                var uiText by remember { mutableStateOf("") }
//                var uiIsRecording by remember { mutableStateOf(false) }
//
//                // 権限リクエスト（初回起動/未許可時）
//                LaunchedEffect(Unit) {
//                    if (!hasMicPermission) {
//                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
//                    }
//                }
//                // 権限OKになったら一度だけモデル初期化
//                LaunchedEffect(hasMicPermission) {
//                    if (hasMicPermission && !modelReady) {
//                        runCatching {
//                            initModel()
//                            modelReady = true
//                        }.onFailure {
//                            Log.e(TAG, "Failed to initialize model", it)
//                        }
//                    }
//                }
//
//                // ====== メインUI ======
//                TranscriptionScreen(
//                    hasPermission = hasMicPermission,
//                    modelReady = modelReady,
//                    isRecording = uiIsRecording,
//                    text = uiText,
//                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
//                    onToggle = {
//                        if (uiIsRecording) {
//                            stopRecordingIfNeeded(onText = { uiText = it })
//                            uiIsRecording = false
//                        } else {
//                            if (!initMicrophone()) return@TranscriptionScreen
//                            audioRecord?.startRecording()
//                            isRecording = true
//                            committedText = ""
//                            lastUiRendered = ""
//                            segmentIndex = 0
//                            uiText = ""
//
//                            recordingThread = thread(
//                                start = true, isDaemon = true, name = "AudioThread"
//                            ) {
//                                processSamplesLoop { txt ->
//                                    runOnUiThread { uiText = txt }
//                                }
//                            }
//                            uiIsRecording = true
//                        }
//                    }
//                )
//            }
//        }
//    }
//
//    override fun onStop() {
//        super.onStop()
//        stopRecordingIfNeeded()
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        stopRecordingIfNeeded()
//        runCatching { recognizer.close() }
//            .onFailure { Log.w(TAG, "Recognizer close ignored", it) }
//    }
//
//    // =========================================================================
//    // Compose UI本体
//    // =========================================================================
//
//    @Composable
//    private fun TranscriptionScreen(
//        hasPermission: Boolean,
//        modelReady: Boolean,
//        isRecording: Boolean,
//        text: String,
//        onRequestPermission: () -> Unit = {},
//        onToggle: () -> Unit,
//    ) {
//        val scroll = rememberScrollState()
//        Surface(Modifier.fillMaxSize()) {
//            Column(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .padding(16.dp),
//                verticalArrangement = Arrangement.spacedBy(12.dp)
//            ) {
//                Text(
//                    text = "ONNX Streaming STT (sherpa-onnx)",
//                    style = MaterialTheme.typography.titleMedium
//                )
//
//                when {
//                    !hasPermission -> {
//                        Text("マイク権限が必要です。")
//                        Button(onClick = onRequestPermission) { Text("権限を許可") }
//                    }
//                    !modelReady -> {
//                        Text("モデル初期化中…")
//                        LinearProgressIndicator(Modifier.fillMaxWidth())
//                    }
//                    else -> {
//                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
//                            Button(onClick = onToggle, enabled = modelReady) {
//                                Text(if (isRecording) "停止" else "録音開始")
//                            }
//                        }
//                        Divider()
//                        Text(
//                            text = text.ifBlank { "ここに逐次認識結果が表示されます…" },
//                            modifier = Modifier
//                                .weight(1f)
//                                .fillMaxWidth()
//                                .verticalScroll(scroll),
//                            maxLines = Int.MAX_VALUE,
//                            overflow = TextOverflow.Clip
//                        )
//                    }
//                }
//            }
//        }
//    }
//
//    // =========================================================================
//    // sherpa-onnx 初期化
//    // =========================================================================
//
//    /**
//     * OnlineRecognizer 構築（モデル/特徴量/辞書等の指定）。
//     */
//    private fun initModel() {
//        val modelType = 0
//        val useHomophoneReplacer = false
//        val hr = HomophoneReplacerConfig(
//            dictDir = "dict",
//            lexicon = "lexicon.txt",
//            ruleFsts = "replace.fst"
//        )
//
//        val config = OnlineRecognizerConfig(
//            featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE_HZ, featureDim = 80),
//            modelConfig = requireNotNull(getModelConfig(type = modelType)) {
//                "ModelConfig must not be null"
//            },
//            lmConfig = getOnlineLMConfig(type = modelType),
//            endpointConfig = getEndpointConfig(),
//            enableEndpoint = true,
//        ).also {
//            if (useHomophoneReplacer) {
//                if (hr.dictDir.isNotEmpty() && hr.dictDir.first() != '/') {
//                    val newDir = copyDataDir(hr.dictDir)
//                    hr.dictDir = "$newDir/${hr.dictDir}"
//                }
//                it.hr = hr
//            }
//        }
//
//        recognizer = OnlineRecognizer(
//            assetManager = application.assets,
//            config = config,
//        )
//        Log.i(TAG, "OnlineRecognizer ready: $recognizer")
//    }
//
//    // =========================================================================
//    // 録音処理・ASR
//    // =========================================================================
//
//    /** 録音停止・後始末＋最終テキストUI反映。 */
//    private fun stopRecordingIfNeeded(onText: ((String) -> Unit)? = null) {
//        isRecording = false
//        runCatching { audioRecord?.stop() }
//        releaseAudioRecord()
//
//        recordingThread?.let { t ->
//            if (t.isAlive) {
//                try { t.join(5_000) } catch (_: InterruptedException) { }
//            }
//        }
//        recordingThread = null
//        onText?.invoke(committedText)
//    }
//
//    /** AudioRecord の release を常に安全に行う。 */
//    private fun releaseAudioRecord() {
//        runCatching { audioRecord?.release() }
//        audioRecord = null
//    }
//
//    /** Paraformer を有効化すべきか。 */
//    private fun isParaformerEnabled(cfg: OnlineRecognizerConfig) =
//        cfg.modelConfig.paraformer.encoder.isNotBlank()
//
//    /**
//     * 音声サンプル投入→デコード→UI反映のバックグラウンドループ。
//     */
//    private fun processSamplesLoop(onText: (String) -> Unit) {
//        Log.i(TAG, "Processing samples...")
//        val stream = recognizer.createStream()
//        val bufferSamples = (CHUNK_SECONDS * SAMPLE_RATE_HZ).toInt().coerceAtLeast(160)
//        val pcm16 = ShortArray(bufferSamples)
//
//        try {
//            while (isRecording) {
//                val readFlags = if (Build.VERSION.SDK_INT >= 23) {
//                    AudioRecord.READ_BLOCKING
//                } else {
//                    0
//                }
//                val n = audioRecord?.read(pcm16, 0, pcm16.size, readFlags) ?: break
//
//                if (n <= 0) {
//                    if (n < 0) Log.w(TAG, "AudioRecord.read() returned $n")
//                    continue
//                }
//
//                // PCM16 → float（[-1,1]正規化）
//                val samples = FloatArray(n) { i -> pcm16[i] / 32768.0f }
//                stream.acceptWaveform(samples, sampleRate = SAMPLE_RATE_HZ)
//
//                // decode できるだけ回す
//                while (recognizer.isReady(stream)) {
//                    recognizer.decode(stream)
//                }
//
//                val reachedEndpoint = recognizer.isEndpoint(stream)
//                var hypothesis = recognizer.getResult(stream).text
//
//                // Paraformerの場合はエンドポイント時に末尾に無音追加
//                if (reachedEndpoint && isParaformerEnabled(recognizer.config)) {
//                    val tail = FloatArray((PARAFORMER_TAIL_SECONDS * SAMPLE_RATE_HZ).toInt())
//                    stream.acceptWaveform(tail, sampleRate = SAMPLE_RATE_HZ)
//                    while (recognizer.isReady(stream)) {
//                        recognizer.decode(stream)
//                    }
//                    hypothesis = recognizer.getResult(stream).text
//                }
//
//                // 表示テキスト生成
//                val textToDisplay = if (hypothesis.isNotBlank()) {
//                    buildString {
//                        if (committedText.isNotBlank()) append(committedText).append('\n')
//                        append("$segmentIndex: $hypothesis")
//                    }
//                } else {
//                    committedText
//                }
//
//                // セグメント確定処理
//                if (reachedEndpoint) {
//                    recognizer.reset(stream)
//                    if (hypothesis.isNotBlank()) {
//                        committedText = if (committedText.isBlank()) {
//                            "$segmentIndex: $hypothesis"
//                        } else {
//                            "$committedText\n$segmentIndex: $hypothesis"
//                        }
//                        segmentIndex += 1
//                    }
//                }
//
//                // UIへ必要な時のみ反映
//                if (textToDisplay != lastUiRendered) {
//                    lastUiRendered = textToDisplay
//                    onText(textToDisplay)
//                }
//            }
//        } catch (t: Throwable) {
//            Log.e(TAG, "ASR loop error", t)
//        } finally {
//            // 終了時に最終 flush
//            try {
//                stream.inputFinished()
//                while (recognizer.isReady(stream)) {
//                    recognizer.decode(stream)
//                }
//                val finalText = recognizer.getResult(stream).text
//                if (finalText.isNotBlank()) {
//                    committedText = if (committedText.isBlank()) finalText
//                    else "$committedText\n$finalText"
//                    onText(committedText)
//                }
//            } catch (_: Throwable) { }
//        }
//    }
//
//    /**
//     * マイク初期化
//     * - 必要権限・AudioRecord 設定・バッファ確保
//     */
//    private fun initMicrophone(): Boolean {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
//            != PackageManager.PERMISSION_GRANTED
//        ) return false
//
//        val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
//        if (minBytes == AudioRecord.ERROR || minBytes == AudioRecord.ERROR_BAD_VALUE) {
//            Log.e(TAG, "Invalid min buffer size: $minBytes")
//            return false
//        }
//
//        val bufferBytes = (minBytes * 2).coerceAtLeast(minBytes)
//        audioRecord = if (Build.VERSION.SDK_INT >= 23) {
//            AudioRecord.Builder()
//                .setAudioSource(MediaRecorder.AudioSource.MIC)
//                .setAudioFormat(
//                    AudioFormat.Builder()
//                        .setSampleRate(SAMPLE_RATE_HZ)
//                        .setEncoding(AUDIO_FORMAT)
//                        .setChannelMask(CHANNEL_CONFIG)
//                        .build()
//                )
//                .setBufferSizeInBytes(bufferBytes)
//                .build()
//        } else {
//            AudioRecord(
//                MediaRecorder.AudioSource.MIC,
//                SAMPLE_RATE_HZ,
//                CHANNEL_CONFIG,
//                AUDIO_FORMAT,
//                bufferBytes
//            )
//        }
//
//        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
//            Log.e(TAG, "AudioRecord not initialized.")
//            releaseAudioRecord()
//            return false
//        }
//        return true
//    }
//
//    // =========================================================================
//    // HomophoneReplacer 用: assets→外部ファイル コピー
//    // =========================================================================
//
//    /**
//     * assets内ディレクトリを外部ファイル領域へ再帰コピーし、コピー先パスを返す。
//     */
//    private fun copyDataDir(dirInAssets: String): String {
//        Log.i(TAG, "Copying asset dir: $dirInAssets")
//        copyAssetsRecursive(dirInAssets)
//        val newRoot = application.getExternalFilesDir(null)!!.absolutePath
//        Log.i(TAG, "External root: $newRoot")
//        return newRoot
//    }
//
//    /** ディレクトリ/ファイルを再帰的にコピー（既存ならスキップ） */
//    private fun copyAssetsRecursive(path: String) {
//        try {
//            val entries = application.assets.list(path) ?: emptyArray()
//            if (entries.isEmpty()) {
//                copyAssetFile(path)
//            } else {
//                val fullDir = File(application.getExternalFilesDir(null), path)
//                if (!fullDir.exists()) fullDir.mkdirs()
//                entries.forEach { name ->
//                    val child = if (path.isEmpty()) name else "$path/$name"
//                    copyAssetsRecursive(child)
//                }
//            }
//        } catch (ex: IOException) {
//            Log.e(TAG, "Failed to copy assets at $path", ex)
//        }
//    }
//
//    /** ファイル個別コピー（既存ならスキップ） */
//    private fun copyAssetFile(assetPath: String) {
//        val outFile = File(application.getExternalFilesDir(null), assetPath)
//        if (outFile.exists()) return
//        outFile.parentFile?.mkdirs()
//        try {
//            application.assets.open(assetPath).use { input ->
//                FileOutputStream(outFile).use { output ->
//                    val buf = ByteArray(8 * 1024)
//                    while (true) {
//                        val read = input.read(buf)
//                        if (read == -1) break
//                        output.write(buf, 0, read)
//                    }
//                }
//            }
//        } catch (ex: IOException) {
//            Log.e(TAG, "Failed to copy asset file: $assetPath", ex)
//        }
//    }
//}



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
