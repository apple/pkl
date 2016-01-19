plugins {
  `kotlin-dsl`
}

dependencies {
  implementation(libs.downloadTaskPlugin)
  implementation(libs.spotlessPlugin)
  implementation(libs.kotlinPlugin) {
    exclude(module = "kotlin-android-extensions")
  }
  implementation(libs.shadowPlugin)
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}
