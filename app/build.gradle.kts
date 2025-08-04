// ==================================================
// Onnx STT Android アプリ（Compose + JNI）
// - Compose BOM でバージョン一元管理
// - ネイティブモジュール（:nativelib）と連携
// ==================================================

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose) // Compose Compiler の連携はこのプラグインに任せる
}

android {
    namespace = "com.negi.onnxstt"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.negi.onnxstt"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // テストランナー等の追加設定が必要ならここに追記
    }

    buildFeatures {
        compose = true // Compose を有効化
    }

    // Kotlin 2.0.x＋Java 17 を明示的に合わせる
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // 必要があれば追加オプション例: freeCompilerArgs += listOf("-Xjvm-default=all")
    }

    buildTypes {
        release {
            // デモ／開発用途では debug キーを使ってもよいが、本番リリースでは専用の署名を分離すること。
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false // 本番で縮小を使うなら true にし、proguardルールを調整
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Compose Compiler Extension は kotlin.compose プラグインが自動的に連携するので通常明示不要
}

dependencies {
    // --------------------------------------------------
    // ネイティブ連携モジュール
    // --------------------------------------------------
    implementation(project(":nativelib"))

    // --------------------------------------------------
    // Compose：BOM で全体のバージョン整合性を取る
    // --------------------------------------------------
    implementation(platform(libs.androidx.compose.bom))

    // UI 関連（BOM がバージョンを管理するため個別指定不要）
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)

    // --------------------------------------------------
    // AndroidX / ライフサイクル / 基本ユーティリティ
    // --------------------------------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material) // 既存の View 系 Material Components（Compose の Material3 とは別系統）

    // --------------------------------------------------
    // テスト
    // --------------------------------------------------
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom)) // Compose テスト用バージョン整合
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // --------------------------------------------------
    // デバッグ支援
    // --------------------------------------------------
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
