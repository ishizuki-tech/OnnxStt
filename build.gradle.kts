// ==================================================
// Top-level build.gradle.kts
// 共通プラグイン alias を宣言（実際の適用は各モジュール側で行う）
// リポジトリは settings.gradle.kts 側で一元管理する（FAIL_ON_PROJECT_REPOS 前提のためここでは書かない）
// ==================================================

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// --------------------------------------------------
// 拡張ポイント / 参考メモ
// ・共通の設定やタスクを追加したい場合はここに書けるが、**repositories { } は書かないこと**。
//   例: サブプロジェクト共通の lint/verification タスクやバージョンチェックなど。
// ・リポジトリの定義例（これはここではなく settings.gradle.kts に置く）：
//     dependencyResolutionManagement {
//         repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
//         repositories {
//             google()
//             mavenCentral()
//         }
//     }
// --------------------------------------------------

// 例として全サブプロジェクトの clean を束ねるタスク（任意・必要なら有効化）
// tasks.register("cleanAll") {
//     description = "全サブプロジェクトの build ディレクトリをクリーン"
//     group = "build"
//     dependsOn(subprojects.mapNotNull { it.tasks.findByName("clean") })
// }
