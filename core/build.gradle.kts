import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Android に依存しない判断（配置・振り分け・ジェスチャー）を置く。JVM の単体テストだけで確かめられるように分けている
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(libs.junit)
}
