import com.android.build.api.variant.AndroidComponentsExtension

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.tkdev106.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.tkdev106.launcher"
        // 対象は Pixel 7（Android 17）の1台だけなので、古い版への互換は持たない
        minSdk = 37
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

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

/** ドロワーのフォルダの初期値は docs/ に1か所だけ置き、ビルドのたびに APK の assets へ写す */
abstract class CopyDrawerFolders : DefaultTask() {
    @get:InputFile
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.mkdirs()
        source.get().asFile.copyTo(out.resolve("drawer-folders.json"), overwrite = true)
    }
}

val copyDrawerFolders = tasks.register<CopyDrawerFolders>("copyDrawerFolders") {
    source.set(rootProject.layout.projectDirectory.file("docs/drawer-folders.json"))
}

extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(copyDrawerFolders, CopyDrawerFolders::outputDir)
}

dependencies {
    implementation(project(":core"))
}
