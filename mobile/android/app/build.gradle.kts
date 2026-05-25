// App module — packages `libzed_mobile.so` into an APK using NativeActivity.
//
// The native library is produced by `cargo build -p zed_mobile
// --target aarch64-linux-android` (or `cargo ndk`) and copied into
// `app/src/main/jniLibs/arm64-v8a/libzed_mobile.so` by the `buildRustDebug`
// task below. `preBuild` depends on it so a plain `./gradlew assembleDebug`
// is a one-shot build.

plugins {
    id("com.android.application")
}

android {
    namespace = "dev.zed.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.zed.mobile"
        minSdk = 26          // wgpu / Vulkan 1.0 baseline
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += "arm64-v8a"
        }

        manifestPlaceholders["nativeLibraryName"] = "zed_mobile"
    }

    buildTypes {
        debug {
            isDebuggable = true
            isJniDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    packaging {
        // cargo already strips in release; double-stripping breaks backtraces.
        jniLibs.keepDebugSymbols += "*/arm64-v8a/libzed_mobile.so"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Nothing yet — added per-feature when we wire in `gpui_mobile` packages
    // that need app-side Java helpers (e.g. notifications → androidx.core).
}

// ── Rust build integration ──────────────────────────────────────────────────
//
// Drives `cargo build -p zed_mobile --target aarch64-linux-android` and copies
// the resulting `libzed_mobile.so` into `app/src/main/jniLibs/arm64-v8a/`. Wire
// `preBuild` to depend on it so a plain `assembleDebug` (or `assembleRelease`)
// is one-shot.

val workspaceRoot = rootProject.projectDir.parentFile.parentFile

fun ndkBin(): String {
    val home = System.getenv("ANDROID_NDK_HOME")
        ?: System.getenv("ANDROID_NDK_ROOT")
        ?: "/opt/homebrew/share/android-ndk"
    return "$home/toolchains/llvm/prebuilt/darwin-x86_64/bin"
}

fun registerRustBuild(name: String, profileFlag: String?, profileDir: String) {
    tasks.register<Exec>(name) {
        group = "rust"
        description = "Build libzed_mobile.so for arm64-v8a ($profileDir)."
        workingDir = workspaceRoot

        val bin = ndkBin()
        environment("RUST_FONTCONFIG_DLOPEN", "on")
        environment("CC_aarch64_linux_android",  "$bin/aarch64-linux-android26-clang")
        environment("CXX_aarch64_linux_android", "$bin/aarch64-linux-android26-clang++")
        environment("AR_aarch64_linux_android",  "$bin/llvm-ar")
        environment("CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER",
                    "$bin/aarch64-linux-android26-clang")

        val args = mutableListOf("cargo", "build", "-p", "zed_mobile",
                                 "--target", "aarch64-linux-android")
        if (profileFlag != null) args += profileFlag
        commandLine(args)

        doLast {
            val src = file("$workspaceRoot/target/aarch64-linux-android/$profileDir/libzed_mobile.so")
            val dst = file("src/main/jniLibs/arm64-v8a/libzed_mobile.so")
            dst.parentFile.mkdirs()
            src.copyTo(dst, overwrite = true)
        }
    }
}

registerRustBuild("buildRustDebug",   null,         "debug")
registerRustBuild("buildRustRelease", "--release",  "release")

tasks.named("preBuild") {
    dependsOn(
        if (project.hasProperty("rustRelease")) "buildRustRelease" else "buildRustDebug",
    )
}
