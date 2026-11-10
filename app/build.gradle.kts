plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.tkdev106.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.tkdev106.launcher"
        // 対象は Pixel 7（Android 17）の1台だけなので、古い版への互換は持たない
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        // カスタムアイコンの原本は assets/icons/ に1か所だけ置き、そのまま APK に入れる
        getByName("main").assets.srcDir("../assets")
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    implementation(project(":core"))
}
