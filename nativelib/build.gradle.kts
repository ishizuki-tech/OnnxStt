// ==================================================
// sherpa-onnx / Sherpa-NCNN 共通 JNI ラッパーライブラリモジュール設定
// 目的：arm64-v8a 向けネイティブバイナリを含む AAR を安定的にビルドする。
// Version Catalog（libs.*） を前提に依存を一元管理。
// ==================================================

plugins {
    alias(libs.plugins.android.library) // AAR 出力用の Android ライブラリモジュール
    alias(libs.plugins.kotlin.android)  // Kotlin Android サポート
}

android {
    namespace = "com.k2fsa.sherpa.onnx" // ソースパッケージと整合させた名前空間
    compileSdk = 35                     // 最新安定 SDK をターゲット（必要に応じて更新）

    defaultConfig {
        minSdk = 24 // アプリ側と合わせた最低 API レベル（互換性確保）

        // 対応 ABI を絞ることで不要なバイナリを除外（現状は arm64-v8a のみ）
        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a")
        }

        // AAR を取り込む側に ProGuard 保持ルールを伝搬
        consumerProguardFiles("consumer-rules.pro")
    }

    // ネイティブライブラリ配置ディレクトリを明示（デフォルトでも機能するが明確化）
    sourceSets["main"].jniLibs.srcDirs("src/main/jniLibs")

    // ビルド出力の最適化：必要なければ BuildConfig クラスを生成しない
    buildFeatures {
        buildConfig = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false // 本番で縮小を使うなら true に切り替え、ルールを調整する
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Java / Kotlin 両方を Java 17 に合わせてコンパイル
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            // 依存先が同じネイティブ .so（例: libc++_shared.so）を含むときの衝突回避
            pickFirsts += setOf("**/libc++_shared.so")
        }
    }
}

dependencies {
    // 軽量な注釈ユーティリティ。JNI ラッパーのみなら UI 系は含めない方が依存を最小化できる。
    implementation(libs.androidx.annotation)

    // インストルメンテーションテスト用（必要が出たときだけ含める）
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
