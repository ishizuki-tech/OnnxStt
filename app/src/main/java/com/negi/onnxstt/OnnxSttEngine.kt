package com.negi.onnxstt

import android.app.Application
import android.util.Log
import com.k2fsa.sherpa.onnx.*

/**
 * sherpa-onnx の音声認識器（OnlineRecognizer）を一元管理するラッパークラス。
 *
 * - モデル・特徴量・エンドポイント等の初期化を集約し、必要に応じて拡張も容易
 * - アプリ全体で単一インスタンスとして扱うことを想定
 * - 利用前に [initModel] でモデル初期化が必要
 */
class OnnxSttEngine(private val application: Application) {

    /**
     * 認識器本体（初期化後のみ利用可）
     */
    lateinit var recognizer: OnlineRecognizer
        private set

    /**
     * モデル・特徴量・設定をまとめて初期化し、[recognizer]を生成する。
     *
     * - 必要に応じて `modelType` 等を柔軟にパラメータ化して拡張可。
     * - 初期化失敗時は例外を投げる（呼び出し側でcatch推奨）。
     */
    fun initModel(
        modelType: Int = 0,
        sampleRate: Int = 16000,
        featureDim: Int = 80
    ) {
        // モデル種別ごとの構成取得（将来拡張しやすい）
        val config = OnlineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = sampleRate, featureDim = featureDim),
            modelConfig = requireNotNull(getModelConfig(type = modelType)) {
                "ModelConfig must not be null"
            },
            lmConfig = getOnlineLMConfig(type = modelType),
            endpointConfig = getEndpointConfig(),
            enableEndpoint = true,
        )
        // assets からモデルを解決
        recognizer = OnlineRecognizer(application.assets, config)
        Log.i(TAG, "OnlineRecognizer ready: $recognizer")
    }

    /**
     * リソース解放（OnlineRecognizer の close を呼ぶ）。
     * 必ず不要時・アプリ終了時に呼び出してください。
     */
    fun release() {
        runCatching { recognizer.close() }
            .onFailure { Log.w(TAG, "Recognizer close failed", it) }
    }

    companion object {
        private const val TAG = "OnnxSttEngine"
    }
}
