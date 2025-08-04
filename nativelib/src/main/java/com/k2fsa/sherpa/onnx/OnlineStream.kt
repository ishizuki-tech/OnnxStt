package com.k2fsa.sherpa.onnx

import android.util.Log
import java.io.Closeable

/**
 * sherpa-onnx のオンライン音声ストリームをラップするクラス。
 *
 * - OnlineRecognizer から生成され、音声データ供給・デコード・リソース管理の単位となります。
 * - 明示的に [close] または Kotlin標準の `use { ... }` で確実に解放してください。
 *   finalize() に頼る運用は推奨しません。
 */
class OnlineStream internal constructor(internal var ptr: Long) : Closeable {

    companion object {
        private const val TAG = "OnlineStream"

        init {
            // JNI ライブラリのロード（冪等なので複数回呼んでも安全）
            runCatching { System.loadLibrary("sherpa-onnx-jni") }
        }
    }

    init {
        require(ptr != 0L) { "OnlineStream native pointer is 0 (invalid)" }
    }

    /** ストリームが有効かチェック。close後の操作ミスを即時検出するために使用。 */
    private inline fun checkOpen() = check(ptr != 0L) { "OnlineStream is already closed." }

    /**
     * 音声波形データ（float配列）をネイティブ側へ供給する。
     *
     * @param samples 正規化済み音声データ（[-1, 1] の float配列）
     * @param sampleRate サンプリングレート（例: 16000）
     */
    fun acceptWaveform(samples: FloatArray, sampleRate: Int) {
        checkOpen()
        acceptWaveform(ptr, samples, sampleRate)
    }

    /**
     * 入力終了をネイティブ側に通知する。
     * 追加の音声供給が不要な場合、最後に呼んでください。
     */
    fun inputFinished() {
        checkOpen()
        inputFinished(ptr)
    }

    /**
     * ネイティブリソースを明示的に解放する。
     * 2回目以降は no-op なので多重呼び出し安全です。
     */
    @Synchronized
    override fun close() {
        if (ptr != 0L) {
            try {
                delete(ptr)
            } catch (t: Throwable) {
                Log.w(TAG, "delete() threw an exception; ignoring", t)
            } finally {
                ptr = 0L
            }
        }
    }

    /**
     * Kotlin 標準の `use { ... }` 構文を推奨します。
     * 旧APIとの互換用メソッド（非推奨）。
     */
    @Deprecated(
        message = "標準の Closeable.use { ... } をご利用ください。",
        replaceWith = ReplaceWith("use(block)")
    )
    fun use(block: (OnlineStream) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }

    /**
     * GCによる自動解放の緊急保険。必ず [close] を明示的に呼んでください。
     */
    @Suppress("deprecation")
    protected fun finalize() {
        close()
    }

    // ===== JNI ネイティブ関数宣言（C++と名前一致させること） =====

    /** ネイティブに波形データを送信 */
    private external fun acceptWaveform(ptr: Long, samples: FloatArray, sampleRate: Int)

    /** ネイティブに入力終了を通知 */
    private external fun inputFinished(ptr: Long)

    /** ネイティブリソース解放 */
    private external fun delete(ptr: Long)
}
