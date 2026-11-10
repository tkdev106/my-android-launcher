plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

// 完了の定義（CLAUDE.md）。lint → 単体テスト → デバッグ APK の順に走らせる
tasks.register("verify") {
    group = "verification"
    description = "lint・単体テスト・デバッグ APK のビルドをまとめて走らせる"
    dependsOn(":app:lintDebug", ":core:test", ":app:testDebugUnitTest", ":app:assembleDebug")
}

gradle.projectsEvaluated {
    val lint = project(":app").tasks.named("lintDebug")
    val coreTest = project(":core").tasks.named("test")
    val appTest = project(":app").tasks.named("testDebugUnitTest")
    val assemble = project(":app").tasks.named("assembleDebug")
    coreTest.configure { mustRunAfter(lint) }
    appTest.configure { mustRunAfter(coreTest) }
    assemble.configure { mustRunAfter(appTest) }
}
