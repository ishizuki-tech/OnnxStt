@file:Suppress("JniMissingFunction", "KotlinJniMissingFunction")

package com.k2fsa.sherpa.onnx

import android.content.res.AssetManager
import android.util.Log
import java.io.Closeable

// =====================================================================
// 独自 Keep アノテーション
// =====================================================================

/**
 * ProGuard/R8 で難読化されないようにするマーカーアノテーション。
 * AndroidX依存を避ける場合や小型バイナリを目指す場合に使う。
 *
 * - 使用例:
 *     @Keep data class MyConfig(...)
 *
 * - R8/ProGuard利用時は追加で
 *     -keep class com.k2fsa.sherpa.onnx.** { *; }
 *   などのルールを記述しておくと安心です。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY
)
annotation class Keep

// =====================================================================
// 設定・構成系データクラス群
// =====================================================================

/**
 * 音声特徴量設定。
 * - sampleRate: 入力音声のサンプリングレート（例: 16000Hz）
 * - featureDim: 特徴量次元数（例: 80次元メルスペクトログラム等）
 * - normalizeSamples: 入力PCMの正規化有無（[-1, 1] floatなど）
 */
@Keep
data class FeatureConfig(
    var sampleRate: Int = 16000,
    var featureDim: Int = 80,
    var normalizeSamples: Boolean = true,
    var dither: Float = 0.0f, // ★これを追加
)

fun getFeatureConfig(sampleRate: Int, featureDim: Int): FeatureConfig {
    return FeatureConfig(sampleRate = sampleRate, featureDim = featureDim)
}

/**
 * HomophoneReplacer（同音異義語置換器）の設定情報を保持するデータクラス。
 *
 * - モデル推論前または結果に対し、辞書による正規化や誤認識の補正を行う場合に利用。
 * - 全てのフィールドはオプションで、未使用なら空文字でOK。
 *
 * @property dictDir   辞書ディレクトリパス（例："dict"）。assetsまたは外部ストレージ内の相対パスを指定。
 * @property lexicon   発音辞書ファイル名（例："lexicon.txt"）。認識語彙と発音の対応表。
 * @property ruleFsts  置換ルールFSTファイル名（例："replace.fst"）。変換ルールやフィルタの実装に利用。
 */
@Keep
data class HomophoneReplacerConfig(
    var dictDir: String = "",  // 辞書ディレクトリの相対/絶対パス。空文字で無効。
    var lexicon: String = "",  // 発音辞書ファイル名（辞書ディレクトリ内 or assets直下）。
    var ruleFsts: String = "", // FSTルールファイル名（任意、複雑な変換ルールに対応）。
)

/**
 * エンドポイント検出ルール1件分。
 * - mustContainNonSilence: 無音以外の成分が必要か
 * - minTrailingSilence: セグメント末尾の最小無音長[秒]
 * - minUtteranceLength: セグメント全体の最小長[秒]
 */
@Keep
data class EndpointRule(
    var mustContainNonSilence: Boolean,
    var minTrailingSilence: Float,
    var minUtteranceLength: Float,
)

/**
 * sherpa-onnx推奨の3ルールから成るエンドポイント判定セット。
 */
@Keep
data class EndpointConfig(
    var rule1: EndpointRule = EndpointRule(false, 2.4f, 0.0f),
    var rule2: EndpointRule = EndpointRule(true, 1.4f, 0.0f),
    var rule3: EndpointRule = EndpointRule(false, 0.0f, 20.0f)
)

/**
 * RNN-T/Transducer系モデルパス。
 */
@Keep
data class OnlineTransducerModelConfig(
    var encoder: String = "",
    var decoder: String = "",
    var joiner: String = "",
)

/**
 * Paraformer系モデルパス。
 */
@Keep
data class OnlineParaformerModelConfig(
    var encoder: String = "",
    var decoder: String = "",
)

/**
 * Zipformer2-CTCモデルパス（単一ONNXファイル）。
 */
@Keep
data class OnlineZipformer2CtcModelConfig(
    var model: String = "",
)

/**
 * NeMo Fast-Conformer-CTCモデルパス（単一ONNXファイル）。
 */
@Keep
data class OnlineNeMoCtcModelConfig(
    var model: String = "",
)

/**
 * 総合モデル設定。複数方式を切り替え可能。
 */
@Keep
data class OnlineModelConfig(
    var transducer: OnlineTransducerModelConfig = OnlineTransducerModelConfig(),
    var paraformer: OnlineParaformerModelConfig = OnlineParaformerModelConfig(),
    var zipformer2Ctc: OnlineZipformer2CtcModelConfig = OnlineZipformer2CtcModelConfig(),
    var neMoCtc: OnlineNeMoCtcModelConfig = OnlineNeMoCtcModelConfig(),
    var tokens: String = "",
    var numThreads: Int = 1,
    var debug: Boolean = false,
    var provider: String = "cpu",
    var modelType: String = "",
    var modelingUnit: String = "",
    var bpeVocab: String = "",
)

/**
 * 言語モデル設定（主に補助的用途）。
 */
@Keep
data class OnlineLMConfig(
    var model: String = "",
    var scale: Float = 0.5f,
)

/**
 * CTC FST デコーダ設定（必要な場合のみ）。
 */
@Keep
data class OnlineCtcFstDecoderConfig(
    var graph: String = "",
    var maxActive: Int = 3000,
)

/**
 * 音声認識器全体の総合設定。
 * - 認識方式/モデル/エンドポイント/Lexicon/ホットワードなど
 */
@Keep
data class OnlineRecognizerConfig(
    var featConfig: FeatureConfig = FeatureConfig(),
    var modelConfig: OnlineModelConfig = OnlineModelConfig(),
    var lmConfig: OnlineLMConfig = OnlineLMConfig(),
    var ctcFstDecoderConfig: OnlineCtcFstDecoderConfig = OnlineCtcFstDecoderConfig(),
    var hr: HomophoneReplacerConfig = HomophoneReplacerConfig(),
    var endpointConfig: EndpointConfig = EndpointConfig(),
    var enableEndpoint: Boolean = true,
    var decodingMethod: String = "greedy_search",
    var maxActivePaths: Int = 4,
    var hotwordsFile: String = "",
    var hotwordsScore: Float = 1.5f,
    var ruleFsts: String = "",
    var ruleFars: String = "",
    var blankPenalty: Float = 0.0f,
)

/**
 * 認識結果（1デコード分）。
 * - text: デコード済みテキスト
 * - tokens: 出力トークン列（デバッグ等に）
 * - timestamps: 各トークンのタイムスタンプ
 */
@Keep
data class OnlineRecognizerResult(
    val text: String,
    val tokens: Array<String>,
    val timestamps: FloatArray,
)

// =====================================================================
// OnlineRecognizer: JNI ラッパー本体
// =====================================================================

/**
 * sherpa-onnx のオンライン認識器をラップするクラス（JNIバインディング）。
 * - モデル/辞書/設定のロードからストリーム生成、デコード制御まで担当。
 * - 必ず close()/use{} で明示解放してください。
 * - finalize() は緊急用の保険です（GCまかせは非推奨）。
 *
 * Example:
 *   OnlineRecognizer(assetManager, config).use { recognizer ->
 *     recognizer.createStream().use { stream ->
 *        // 波形を供給してデコードする
 *     }
 *   }
 */
class OnlineRecognizer(
    assetManager: AssetManager? = null,
    val config: OnlineRecognizerConfig,
) : Closeable {

    @Volatile
    private var ptr: Long = 0L

    init {
        ptr = try {
            if (assetManager != null) {
                Companion.newFromAsset(assetManager, config)
            } else {
                Companion.newFromFile(config)
            }.also { h ->
                require(h != 0L) { "Native recognizer handle is 0 (construction failed)" }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create OnlineRecognizer", t)
            throw t
        }
    }

    /**
     * 内部ptrを使うときの安全ユーティリティ。
     * 閉じたあとのアクセスミスを即時検出するために使用。
     */
    private inline fun <T> withHandle(block: (Long) -> T): T {
        val h = ptr
        check(h != 0L) { "Recognizer already closed" }
        return block(h)
    }

    /**
     * ネイティブリソースの明示解放。
     * 多重呼び出し/並列呼び出しも安全。
     */
    @Synchronized
    override fun close() {
        if (ptr != 0L) {
            try {
                Companion.delete(ptr)
            } catch (t: Throwable) {
                Log.w(TAG, "delete() threw an exception; ignoring", t)
            } finally {
                ptr = 0L
            }
        }
    }

    /** 既存コードとの互換用エイリアス。 */
    fun release() = close()

    /**
     * finalize() は明示解放できなかった場合の緊急用保険。
     * メモリリーク防止のため必ず close() を使ってください。
     */
    @Suppress("deprecation")
    protected fun finalize() {
        close()
    }

    /**
     * 新しいストリームを生成（1回の音声認識単位）。
     * 必要ならホットワード指定も可能。
     */
    fun createStream(hotwords: String = ""): OnlineStream {
        return withHandle { h ->
            val p = Companion.createStream(h, hotwords)
            OnlineStream(p)
        }
    }

    /** ストリームの状態をリセット */
    fun reset(stream: OnlineStream) {
        withHandle { h -> Companion.reset(h, stream.ptr) }
    }

    /** ストリームに対してデコードを進める */
    fun decode(stream: OnlineStream) {
        withHandle { h -> Companion.decode(h, stream.ptr) }
    }

    /** 現在のストリームがエンドポイント（終了）に達したかどうか判定 */
    fun isEndpoint(stream: OnlineStream): Boolean {
        return withHandle { h -> Companion.isEndpoint(h, stream.ptr) }
    }

    /** ストリームがデコード可能（ready）か判定 */
    fun isReady(stream: OnlineStream): Boolean {
        return withHandle { h -> Companion.isReady(h, stream.ptr) }
    }

    /**
     * 現在の認識仮説（テキスト・トークン・タイムスタンプ）を取得。
     * JNI側から配列が返るため要素数チェックあり。
     */
    fun getResult(stream: OnlineStream): OnlineRecognizerResult {
        return withHandle { h ->
            val objArray = Companion.getResult(h, stream.ptr)
            if (objArray.size < 3) {
                Log.w(TAG, "Unexpected result size=${objArray.size}; returning empty result")
                return@withHandle OnlineRecognizerResult("", emptyArray(), FloatArray(0))
            }

            val text = (objArray[0] as? String) ?: ""
            @Suppress("UNCHECKED_CAST")
            val tokens = (objArray[1] as? Array<String>) ?: emptyArray()
            val timestamps = (objArray[2] as? FloatArray) ?: FloatArray(0)

            OnlineRecognizerResult(text = text, tokens = tokens, timestamps = timestamps)
        }
    }

    companion object {
        private const val TAG = "OnlineRecognizer"

        /**
         * 必要なJNIライブラリをロード。
         * - "c++_shared"や"onnxruntime"はビルド設定によっては既にロード済みでもOK。
         * - 失敗時はログと例外throwで明示。
         */
        init {
            runCatching { System.loadLibrary("c++_shared") }
            runCatching { System.loadLibrary("onnxruntime") }
            runCatching {
                System.loadLibrary("sherpa-onnx-jni")
                Log.i(TAG, "Native library loaded.")
            }.onFailure {
                Log.e(TAG, "Failed to load", it)
                throw it
            }
        }

        // ===== JNIブリッジ（private/internalにして実装流出を防ぐ） =====
        @JvmStatic external fun delete(ptr: Long)
        @JvmStatic external fun newFromAsset(assetManager: AssetManager, config: OnlineRecognizerConfig): Long
        @JvmStatic external fun newFromFile(config: OnlineRecognizerConfig): Long
        @JvmStatic external fun createStream(ptr: Long, hotwords: String): Long
        @JvmStatic external fun reset(ptr: Long, streamPtr: Long)
        @JvmStatic external fun decode(ptr: Long, streamPtr: Long)
        @JvmStatic external fun isEndpoint(ptr: Long, streamPtr: Long): Boolean
        @JvmStatic external fun isReady(ptr: Long, streamPtr: Long): Boolean
        @JvmStatic external fun getResult(ptr: Long, streamPtr: Long): Array<Any>
    }
}

// =====================================================================
// モデル選択・設定ヘルパ関数
// =====================================================================

/**
 * モデル構成を返すサンプル実装。
 * - 必要に応じて type 引数で複数切替可。
 * - assets 直下にモデル/トークンファイル等を配置前提。
 */
fun getModelConfig(type: Int): OnlineModelConfig? {
    return OnlineModelConfig(
        transducer = OnlineTransducerModelConfig(
            encoder = "encoder-epoch-99-avg-1.onnx",
            decoder = "decoder-epoch-99-avg-1.onnx",
            joiner = "joiner-epoch-99-avg-1.onnx",
        ),
        tokens = "tokens.txt",
        modelType = "zipformer",
        provider = "cpu",
        numThreads = 2,
    )
}

/**
 * デフォルトのLM構成（空でOK。多くの用途でLMは未使用）。
 */
fun getOnlineLMConfig(type: Int): OnlineLMConfig = OnlineLMConfig()

/**
 * デフォルトのエンドポイント設定（必要に応じて調整）。
 * - rule1/2/3の閾値を各言語・話速に合わせて調整推奨。
 */
fun getEndpointConfig(): EndpointConfig = EndpointConfig(
    rule1 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 2.4f, minUtteranceLength = 0.0f),
    rule2 = EndpointRule(mustContainNonSilence = true, minTrailingSilence = 1.4f, minUtteranceLength = 0.0f),
    rule3 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 0.0f, minUtteranceLength = 20.0f),
)
