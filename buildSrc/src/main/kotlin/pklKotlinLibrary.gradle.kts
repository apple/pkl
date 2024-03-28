import org.gradle.accessors.dm.LibrariesForLibs

plugins {
  id("pklJavaLibrary")

  kotlin("jvm")
}

val buildInfo = project.extensions.getByType<BuildInfo>()

// Version Catalog library symbols.
val libs = the<LibrariesForLibs>()

dependencies {
  // At least some of our kotlin APIs contain Kotlin stdlib types
  // that aren't compiled away by kotlinc (e.g., `kotlin.Function`).
  // So let's be conservative and default to `api` for now.
  // For Kotlin APIs that only target Kotlin users (e.g., pkl-config-kotlin),
  // it won't make a difference.
  api(buildInfo.libs.findLibrary("kotlinStdLib").get())
}

tasks.compileKotlin {
  enabled = true // disabled by pklJavaLibrary
}

kotlin.jvmToolchain {
  languageVersion.set(jvmToolchainVersion)
  vendor.set(jvmToolchainVendor)
}

spotless {
  kotlin {
    ktfmt(libs.versions.ktfmt.get()).googleStyle()
    targetExclude("**/generated/**", "**/build/**")
    licenseHeaderFile(rootProject.file("buildSrc/src/main/resources/license-header.star-block.txt"))
  }
}
