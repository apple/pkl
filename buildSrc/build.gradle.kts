plugins {
  `kotlin-dsl`
}

dependencies {
  implementation(libs.downloadTaskPlugin)
  implementation(libs.spotlessPlugin)
  implementation(libs.kotlinPlugin) {
    exclude(module = "kotlin-android-extensions")
  }
  implementation(libs.kotlinPluginSerialization)
  implementation(libs.shadowPlugin)

  // fix from the Gradle team: makes version catalog symbols available in build scripts
  // see here for more: https://github.com/gradle/gradle/issues/15383
  implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}
