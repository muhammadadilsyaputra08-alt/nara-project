pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    // Modul :llama (examples/llama.android/lib, lihat di bawah) pakai version catalog
    // (libs.xxx) miliknya sendiri di examples/llama.android/gradle/libs.versions.toml.
    // Project ini tidak punya katalog "libs" sendiri, jadi kita pinjam punya llama.android
    // supaya referensi libs.xxx di build.gradle.kts modul itu bisa resolve.
    val llamaCatalogFile = File(rootDir, "external/llama.cpp/examples/llama.android/gradle/libs.versions.toml")
    if (llamaCatalogFile.exists()) {
        versionCatalogs {
            create("libs") {
                from(files(llamaCatalogFile))
            }
        }
    }
}

rootProject.name = "NaraAssistant"
include(":app")

// Integrasi native llama.cpp Android (inference GGUF).
// Modul :llama menunjuk ke examples/llama.android/lib di dalam clone llama.cpp
// yang di-checkout terpisah oleh CI (lihat .github/workflows/build-apk.yml) ke
// folder external/llama.cpp, dipatok ke tag rilis tertentu (bukan auto-track
// branch utama) supaya struktur modul tidak berubah diam-diam antar build.
// Untuk build lokal (Android Studio), clone manual:
//   git clone --depth 1 --branch b10076 https://github.com/ggml-org/llama.cpp.git external/llama.cpp
val llamaLibDir = File(rootDir, "external/llama.cpp/examples/llama.android/lib")
if (llamaLibDir.exists()) {
    include(":llama")
    project(":llama").projectDir = llamaLibDir
} else {
    println("PERINGATAN: external/llama.cpp belum ada — modul :llama dilewati. Inference GGUF tidak akan aktif.")
}
