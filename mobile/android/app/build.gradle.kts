// App module — packages `libzed_mobile.so` into an APK using NativeActivity.
//
// The native library is produced by `cargo ndk -t arm64-v8a build -p zed_mobile`
// and copied into `app/src/main/jniLibs/arm64-v8a/libzed_mobile.so` by the
// `buildRustDebug` task below. `preBuild` depends on it so a plain
// `./gradlew assembleDebug` is a one-shot build.
//
// Requires `cargo-ndk` on $PATH (`cargo install cargo-ndk`) and an NDK
// reachable via $ANDROID_NDK_HOME / $ANDROID_HOME/ndk/* / the Android Studio
// bundled NDK. cargo-ndk handles CC/AR/linker setup itself.

import java.util.Properties

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
// Drives `cargo ndk -t arm64-v8a build -p zed_mobile` and copies the resulting
// `libzed_mobile.so` into `app/src/main/jniLibs/arm64-v8a/`. cargo-ndk auto-
// discovers the NDK and injects CC/AR/linker for the target, so we don't
// hardcode any NDK paths here. `preBuild` depends on this task so a plain
// `assembleDebug` (or `assembleRelease`) is one-shot.

val workspaceRoot = rootProject.projectDir.parentFile.parentFile

fun resolveNdkHome(): String {
    System.getenv("ANDROID_NDK_HOME")?.takeIf { it.isNotBlank() }?.let { return it }
    System.getenv("ANDROID_NDK_ROOT")?.takeIf { it.isNotBlank() }?.let { return it }
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) {
        val props = Properties().apply { localProps.inputStream().use { load(it) } }
        props.getProperty("ndk.dir")?.takeIf { it.isNotBlank() }?.let { return it }
    }
    error("No NDK found. Set ANDROID_NDK_HOME or add ndk.dir=... to local.properties.")
}

fun registerRustBuild(name: String, profileFlag: String?, profileDir: String) {
    tasks.register<Exec>(name) {
        group = "rust"
        description = "Build libzed_mobile.so for arm64-v8a ($profileDir)."
        workingDir = workspaceRoot

        environment("ANDROID_NDK_HOME", resolveNdkHome())

        val args = mutableListOf(
            "cargo", "ndk",
            "-t", "arm64-v8a",
            "-P", "26",                 // matches android.defaultConfig.minSdk
            "--link-libcxx-shared",     // gpui_mobile depends on libc++_shared
            "build", "-p", "zed_mobile",
        )
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
