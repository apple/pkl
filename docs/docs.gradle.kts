import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
  pklAllProjects
  pklKotlinTest
}

sourceSets {
  test {
    java {
      srcDir(file("modules/pkl-core/examples"))
      srcDir(file("modules/pkl-config-java/examples"))
    }
    val kotlin = project.extensions
      .getByType<KotlinJvmProjectExtension>()
      .sourceSets[name]
      .kotlin
    kotlin.srcDir(file("modules/pkl-config-kotlin/examples"))
  }
}

dependencies {
  testImplementation(project(":pkl-core"))
  testImplementation(project(":pkl-config-java"))
  testImplementation(project(":pkl-config-kotlin"))
  testImplementation(project(":pkl-commons-test"))
  testImplementation(libs.junitEngine)
  testImplementation(libs.antlrRuntime)
}

tasks.test {
  inputs.files(fileTree("modules").matching {
    include("**/pages/*.adoc")
  })
}
