plugins {
  `kotlin-dsl`
}

// IntelliJ doesn't currently understand statically typed access to the version catalog from this file.
// To avoid false errors, use the dynamic API.
@Suppress("UnstableApiUsage")
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

@Suppress("UnstableApiUsage")
dependencies {
  implementation(libs.findLibrary("downloadTaskPlugin").get())
  implementation(libs.findLibrary("spotlessPlugin").get())
  implementation(libs.findLibrary("kotlinPlugin").get()) {
    exclude(module = "kotlin-android-extensions")
  }
  implementation(libs.findLibrary("shadowPlugin").get())
}

// https://youtrack.jetbrains.com/issue/KT-48745
// Setting both Java and Kotlin to 11 still runs into the above issue (in buildSrc).
java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}
