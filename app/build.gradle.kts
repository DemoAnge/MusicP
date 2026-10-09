import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.music"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.dmusic"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        val youtubeKey = run {
            val file = rootProject.file("local.properties")
            if (!file.exists()) return@run ""
            val props = Properties()
            file.inputStream().use { props.load(it) }
            props.getProperty("YOUTUBE_API_KEY", "").orEmpty()
        }
        val escaped = youtubeKey.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        buildConfigField("String", "YOUTUBE_API_KEY", "\"$escaped\"")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.configureEach {
    if (name != "assembleRelease") return@configureEach
    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        if (!apk.exists()) return@doLast
        val dest = rootProject.layout.projectDirectory.file("apk/MusicP.apk").asFile
        dest.parentFile.mkdirs()
        apk.copyTo(dest, overwrite = true)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.rt.compose)
    implementation(libs.androidx.lifecycle.vm.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.androidx.media)

    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser)
}
