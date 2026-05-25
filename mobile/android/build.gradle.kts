// Root build for the Zed mobile Android app.
//
// The native side is built by cargo (see `mobile/android/app/build.gradle.kts`
// preBuild hook). This file only carries plugin classpath and a top-level
// `clean` task.

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.1.0")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
