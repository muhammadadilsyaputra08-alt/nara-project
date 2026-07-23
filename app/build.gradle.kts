plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.axel.nara"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.axel.nara2"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Modul native llama.cpp (:llama) dibangun untuk arm64-v8a — batasi ABI target
        // supaya APK tidak menyertakan library untuk ABI yang tidak dikompilasi.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    // PENTING untuk modul :llama (ggml/llama.cpp): backend CPU (ggml-cpu, ggml-cpu-dotprod,
    // ggml-cpu-i8mm, dst.) dimuat SAAT RUNTIME lewat ggml_backend_load_all_from_path(), yang
    // melakukan dlopen() ke file .so FISIK di nativeLibraryDir. Default AGP modern menyimpan
    // native lib terkompresi DI DALAM APK (extractNativeLibs=false) dan memuatnya langsung dari
    // situ tanpa ekstrak ke disk — cukup untuk library yang di-load lewat System.loadLibrary()
    // biasa (seperti "ai-chat" sendiri), TAPI bikin dlopen() manual ke backend lain gagal
    // menemukan filenya sama sekali → native error "no backends are loaded" (muncul di Kotlin
    // sebagai UnsupportedArchitectureException yang menyesatkan). useLegacyPackaging = true
    // memaksa semua .so diekstrak ke nativeLibraryDir saat install, seperti Android versi lama.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Model inference lokal (GGUF) via llama.cpp — modul :llama di-include di settings.gradle.kts
    // kalau external/llama.cpp sudah di-clone (CI selalu clone, lihat build-apk.yml).
    if (rootProject.findProject(":llama") != null) {
        implementation(project(":llama"))
    }

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
