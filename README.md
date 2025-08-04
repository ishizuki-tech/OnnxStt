# OnnxStt

Android 向けの Whisper ONNX 音声認識 (STT) テンプレート / 実装基盤  
**目的**: ONNX Runtime を使って Whisper 系のモデルを端末上（オフライン含む）で動かし、スワヒリ語・日本語・英語の音声をテキスト化するための Kotlin / Android テンプレート。  
ネイティブ部分は `:nativelib` で拡張可能な構成になっており、Compose などのモダン Android UI と組み合わせてリアルタイム音声認識アプリを構築できます。

## 主な特徴

- Whisper 系モデルの ONNX 推論（エンコーダー / デコーダー）  
- Melスペクトログラム前処理パイプライン（Kotlin 側での実装想定）  
- Swahili / 日本語 / 英語向けの多言語対応（トークナイザー・デコード）  
- Android で動く軽量な音声認識テンプレート（Kotlin + JNI / ネイティブ拡張可能）  
- Gradle + Android Studio でそのままビルド可能（JDK17 / NDK 必要）  

## 目次

- [前提条件](#前提条件)  
- [セットアップとビルド](#セットアップとビルド)  
- [モデルの準備](#モデルの準備)  
- [使い方（サンプル）](#使い方サンプル)  
- [よくある問題と対処](#よくある問題と対処)  
- [貢献](#貢献)  
- [ライセンス](#ライセンス)  
- [連絡先](#連絡先)

## 前提条件

- Android Studio Flamingo 以降（または互換性のあるバージョン）  
- JDK 17  
- Android NDK（プロジェクトの `build.gradle.kts` に合わせたバージョン）  
- Gradle Wrapper（リポジトリに含まれている）  
- ONNX Runtime Android（`com.microsoft.onnxruntime:onnxruntime-android`：実運用時は最新安定版を確認してください）  
- Whisper モデル（ONNX 形式に変換済み）  
- マイク録音のパーミッション（`RECORD_AUDIO`）  

## セットアップとビルド

```bash
# SSH 設定済みなら（例: 特殊ホスト設定を使う場合）
git clone git@github-ishizuki-tech:ishizuki-tech/OnnxStt.git
cd OnnxStt

# リモートの更新を取り込む（非 fast-forward や unrelated histories のエラーが出た場合の例）
git fetch origin
git rebase origin/main  # もしくはマージしたいが unrelated history なら --allow-unrelated-histories を使う

# Android Studio で開いてビルド
# あるいは CLI で
./gradlew assembleDebug
````

### モジュール構成の概略

* `app/` : Android アプリ本体（Kotlin, Compose など UI / 認識ロジック）
* `nativelib/` : ネイティブ拡張（必要なら C/C++ で高性能な前処理・補助ロジックを実装）

## モデルの準備

Whisper の公式 PyTorch モデル（`.pt`）をそのまま使うことはできないため、以下の流れで ONNX 形式に変換するか、適合する事前変換済みモデルを用意してください。

1. **PyTorch → ONNX 変換（例）**

    * Whisper の音声前処理（log-Mel スペクトログラム）を再現
    * エンコーダー / デコーダーをそれぞれ ONNX にエクスポート
    * 必要なら量子化（量子化済みでサイズ & 推論負荷軽減）

2. **Tokenizer / デコード対応**

    * 日本語・スワヒリ語・英語用のトークナイザーを実装し、出力トークンをテキストに戻すロジックを用意（サブワードや BPE 等の対応も想定）

3. **モデルの配置**

    * 例: `app/src/main/assets/models/whisper_encoder.onnx`
    * アプリ側で読み込むパスを設定し、ONNX Runtime セッションを初期化

> 変換スクリプトは本リポジトリに含めていない場合があります。OpenAI の Whisper GitHub や `whisper.cpp` / ONNX Runtime のドキュメントを参照し、必要な前処理コードを自作してください。

## 使い方サンプル

Kotlin 側での大まかな流れ：

1. マイクから PCM を取得（`AudioRecord`）
2. 16kHz/16bit など Whisper 想定のフォーマットに変換
3. Mel スペクトログラムに変換
4. ONNX Runtime セッションに入力して推論（エンコーダー→デコーダー）
5. トークン列をデコードして文字列化
6. UI（Compose など）に反映

```kotlin
// 擬似コード例
val ortEnv = OrtEnvironment.getEnvironment()
val sessionOptions = OrtSession.SessionOptions()
val session = ortEnv.createSession("whisper_encoder.onnx", sessionOptions)

// 前処理済みの inputTensor を用意
val inputTensor = OnnxTensor.createTensor(ortEnv, inputArray)

// 推論実行
val results = session.run(mapOf("input" to inputTensor))
val encoderOutput = results[0].value as Array<FloatArray>
// 以降デコーダーへ渡してデコード
```

UI との連携は `ViewModel` で状態管理し、Compose でリアルタイム文字起こしを表示する構成を推奨します。

## よくある問題と対処

### AudioRecord 初期化エラー

* マイクのパーミッション (`RECORD_AUDIO`) を事前に確認・要求する。
* サンプルレート / バッファサイズが適切か。デバイス固有の制約をチェック。
* `largeHeap` に頼らず、ネイティブメモリと Java ヒープの使い分けを意識する（Whisper 系の大モデルはメモリ使用量に敏感）。

### 非 fast-forward エラー / unrelated histories

* リモートと履歴が乖離している場合、`git fetch` して `git rebase origin/main` するか、新しいブランチを作って差分を整理してください。
* 強制マージが必要なときは慎重に `--allow-unrelated-histories` を使う。

### メモリ制限（大型モデルを載せたとき）

* Android プロセスごとのヒープは端末と OS バージョンで違う。必要ならモデルを小さくする（量子化 / 軽量版）か、ストリーミング方式に分割。
* 大きなネイティブバッファは Java ヒープ外だが、全体の使用量に注意。

## 貢献

プルリクエスト、Issue いずれも大歓迎です。

* 機能追加（例: 新しい言語対応、量子化スクリプト、トークナイザー改善）
* バグ修正
* ドキュメントの改善

**手順例**:

1. フォークしてブランチを作成
2. 変更を加える
3. テストしてコミット
4. プルリクエストを送る

## ライセンス

現時点では明示的なライセンスファイルがありません。
推奨: \[MIT License] を使う場合は `LICENSE` ファイルを追加してください。

例（短縮）:

```text
MIT License

Copyright (c) 2025 Shu Ishizuki

Permission is hereby granted...
```

## 連絡先

* Issue を通じて報告・質問してください。
* 開発者: ishizuki-tech / Shu（GitHub プロフィール経由）

```
