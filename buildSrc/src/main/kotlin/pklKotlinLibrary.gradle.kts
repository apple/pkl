plugins {
  id("pklJavaLibrary")

  kotlin("jvm")
}

val buildInfo = project.extensions.getByType<BuildInfo>()

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

spotless {
  kotlin {
    ktfmt("0.44").kotlinlangStyle()
    targetExclude("**/generated/**", "**/build/**")
    licenseHeaderFile(rootProject.file("buildSrc/src/main/resources/license-header.star-block.txt"))
  }
}
