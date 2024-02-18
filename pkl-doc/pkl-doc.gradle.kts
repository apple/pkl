import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  pklAllProjects
  pklKotlinLibrary
  pklPublishLibrary
  pklHtmlValidator
  @Suppress("DSL_SCOPE_VIOLATION") // https://youtrack.jetbrains.com/issue/KTIJ-19369
  id(libs.plugins.kotlinxSerialization.get().pluginId)
}

val graalVmBaseDir = buildInfo.graalVm.baseDir

dependencies {
  implementation(projects.pklCore)
  implementation(projects.pklCommonsCli)
  implementation(projects.pklCommons)
  implementation(libs.commonMark)
  implementation(libs.commonMarkTables)
  implementation(libs.kotlinxHtml)
  implementation(libs.kotlinxSerializationJson) {
    // use our own Kotlin version
    // (exclude is supported both for Maven and Gradle metadata, whereas dependency constraints aren't)
    exclude(group = "org.jetbrains.kotlin")
  }

  testImplementation(projects.pklCommonsTest)
  testImplementation(libs.jimfs)

  // Graal.JS
  testImplementation(libs.graalSdk)
  testImplementation(libs.graalJs)
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-doc")
        description.set("Documentation generator for Pkl modules.")
      }
    }
  }
}

tasks.jar {
  manifest {
    attributes += mapOf("Main-Class" to "org.pkl.doc.Main")
  }
}

htmlValidator {
  sources = files("src/test/files/DocGeneratorTest/output")
}
