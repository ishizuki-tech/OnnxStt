package com.negi.onnxstt

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import kotlin.concurrent.thread

/**
 * 音声録音およびASRストリーム管理クラス。
 *
 * - 録音開始/停止、AudioRecordのセットアップ・破棄を一元管理
 * - 録音中は音声チャンクごとにASRエンジンへPCMデータを投入＆デコード
 * - セグメント確定/途中経過のテキスト表示管理も担当
 *
 * 利用例:
 *   val audioMgr = AudioRecorderManager()
 *   if (audioMgr.initMicrophone(context)) {
 *     audioMgr.startRecording(sttEngine) { text -> /* UI更新 */ }
 *     ...
 *     audioMgr.stopRecording()
 *   }
 */
class AudioRecorderManager {

    companion object {
        private const val TAG = "AudioRecorderManager"
        private const val SAMPLE_RATE_HZ = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SECONDS = 0.1f
        private const val PARAFORMER_TAIL_SECONDS = 0.8f
    }

    // AudioRecord本体と録音管理スレッド
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile private var isRecording = false

    // セグメント管理・UI反映用変数
    private var segmentIndex = 0
    private var committedText = ""    // 確定済みテキスト
    private var lastUiRendered = ""   // 直近UIに反映した内容

    /**
     * マイク初期化: AudioRecordのセットアップ
     *
     * @return 準備成功時 true、失敗時 false
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun initMicrophone(context: Context): Boolean {
        val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBytes == AudioRecord.ERROR || minBytes == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid min buffer size: $minBytes")
            return false
        }
        val bufferBytes = (minBytes * 2).coerceAtLeast(minBytes)

        audioRecord = if (Build.VERSION.SDK_INT >= 23) {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE_HZ)
                        .setEncoding(AUDIO_FORMAT)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .build()
        } else {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferBytes
            )
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized.")
            releaseAudioRecord()
            return false
        }
        return true
    }

    /**
     * 録音開始＋認識ストリーム開始
     *   - 録音スレッドで音声データを認識器へ流し込み、逐次デコード・UI反映
     *
     * @param sttEngine    音声認識エンジン（OnlineRecognizerを保持）
     * @param onText       テキスト反映コールバック（UI更新用）
     */
    fun startRecording(sttEngine: OnnxSttEngine, onText: (String) -> Unit) {
        audioRecord?.startRecording()
        isRecording = true
        committedText = ""
        lastUiRendered = ""
        segmentIndex = 0

        recordingThread = thread(start = true, isDaemon = true, name = "AudioThread") {
            processSamplesLoop(sttEngine.recognizer, onText)
        }
    }

    /**
     * 録音・認識の停止とリソース後片付け
     */
    fun stopRecording() {
        isRecording = false
        runCatching { audioRecord?.stop() }
        releaseAudioRecord()
        // 録音スレッド終了待ち
        recordingThread?.let { t ->
            if (t.isAlive) {
                try { t.join(5_000) } catch (_: InterruptedException) { /* ignore */ }
            }
        }
        recordingThread = null
    }

    /**
     * AudioRecordの安全な解放
     */
    private fun releaseAudioRecord() {
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    /**
     * 録音ストリームをASRに流し込み、テキスト反映をコールバック
     *
     * @param recognizer    sherpa-onnxのOnlineRecognizer
     * @param onText        UIテキスト反映コールバック
     */
    private fun processSamplesLoop(recognizer: OnlineRecognizer, onText: (String) -> Unit) {
        Log.i(TAG, "Processing samples...")
        val stream = recognizer.createStream()
        val bufferSamples = (CHUNK_SECONDS * SAMPLE_RATE_HZ).toInt().coerceAtLeast(160)
        val pcm16 = ShortArray(bufferSamples)

        try {
            while (isRecording) {
                // 音声データ読み取り
                val readFlags = if (Build.VERSION.SDK_INT >= 23) AudioRecord.READ_BLOCKING else AudioRecord.READ_NON_BLOCKING
                val n = audioRecord?.read(pcm16, 0, pcm16.size, readFlags) ?: break
                if (n <= 0) {
                    if (n < 0) Log.w(TAG, "AudioRecord.read() returned $n")
                    continue
                }
                // PCM16 → float（[-1,1]正規化）
                val samples = FloatArray(n) { i -> pcm16[i] / 32768.0f }
                stream.acceptWaveform(samples, sampleRate = SAMPLE_RATE_HZ)

                // 認識器がreadyな限りdecode（複数回decodeする場合あり）
                while (recognizer.isReady(stream)) recognizer.decode(stream)

                val reachedEndpoint = recognizer.isEndpoint(stream)
                var hypothesis = recognizer.getResult(stream).text

                // Paraformerならエンドポイント時に末尾無音追加→精度向上
                if (reachedEndpoint && recognizer.config.modelConfig.paraformer.encoder.isNotBlank()) {
                    val tail = FloatArray((PARAFORMER_TAIL_SECONDS * SAMPLE_RATE_HZ).toInt())
                    stream.acceptWaveform(tail, sampleRate = SAMPLE_RATE_HZ)
                    while (recognizer.isReady(stream)) recognizer.decode(stream)
                    hypothesis = recognizer.getResult(stream).text
                }

                // 表示用テキスト構築（未確定は行末に上書き）
                val textToDisplay = if (hypothesis.isNotBlank()) {
                    buildString {
                        if (committedText.isNotBlank()) append(committedText).append('\n')
                        append("$segmentIndex: $hypothesis")
                    }
                } else committedText

                // エンドポイント到達時: セグメント確定
                if (reachedEndpoint) {
                    recognizer.reset(stream)
                    if (hypothesis.isNotBlank()) {
                        committedText = if (committedText.isBlank()) {
                            "$segmentIndex: $hypothesis"
                        } else {
                            "$committedText\n$segmentIndex: $hypothesis"
                        }
                        segmentIndex += 1
                    }
                }

                // UIテキストが変わったときだけ更新
                if (textToDisplay != lastUiRendered) {
                    lastUiRendered = textToDisplay
                    onText(textToDisplay)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "ASR loop error", t)
        } finally {
            // 終了後、残りの最終結果flush
            try {
                stream.inputFinished()
                while (recognizer.isReady(stream)) recognizer.decode(stream)
                val finalText = recognizer.getResult(stream).text
                if (finalText.isNotBlank()) {
                    committedText = if (committedText.isBlank()) finalText
                    else "$committedText\n$finalText"
                    onText(committedText)
                }
            } catch (_: Throwable) {}
        }
    }
}
