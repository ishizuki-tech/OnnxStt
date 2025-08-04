package com.negi.onnxstt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 音声認識のメイン画面。
 * - 録音権限・モデル準備・録音状態を反映し、UIを切り替える
 * - 録音開始・停止ボタンや認識テキストの表示を担当
 *
 * @param hasPermission マイク権限があるか
 * @param modelReady    モデル初期化が完了しているか
 * @param isRecording   録音中かどうか
 * @param text          認識結果のテキスト
 * @param onRequestPermission 権限リクエストのコールバック
 * @param onToggle      録音開始/停止トグル時のコールバック
 */
@Composable
fun TranscriptionScreen(
    hasPermission: Boolean,
    modelReady: Boolean,
    isRecording: Boolean,
    text: String,
    onRequestPermission: () -> Unit = {},
    onToggle: () -> Unit,
) {
    val scroll = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // タイトル
            Text(
                text = "ONNX Streaming STT (sherpa-onnx)",
                style = MaterialTheme.typography.titleMedium
            )

            // 状態に応じてUI切り替え
            when {
                // マイク権限がない場合
                !hasPermission -> {
                    Text("マイク権限が必要です。")
                    Button(onClick = onRequestPermission) {
                        Text("権限を許可")
                    }
                }
                // モデル初期化中
                !modelReady -> {
                    Text("モデル初期化中…")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                // モデル＆権限OK、通常操作UI
                else -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onToggle,
                            enabled = modelReady
                        ) {
                            Text(if (isRecording) "停止" else "録音開始")
                        }
                    }
                    // Material3 の区切り線
                    HorizontalDivider()

                    // 認識結果エリア（縦スクロール可）
                    Text(
                        text = text.ifBlank { "ここに逐次認識結果が表示されます…" },
                        modifier = Modifier
                            .weight(1f) // 残り全体を占める
                            .fillMaxWidth()
                            .verticalScroll(scroll),
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}
