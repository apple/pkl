import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
  id("pklAllProjects")
  id("pklKotlinTest")
}

description = "Pkl documentation site"

sourceSets {
  test {
    java {
      srcDir(layout.projectDirectory.file("modules/pkl-core/examples"))
      srcDir(layout.projectDirectory.file("modules/pkl-config-java/examples"))
    }
    val kotlin = project.extensions
      .getByType<KotlinJvmProjectExtension>()
      .sourceSets[name]
      .kotlin
    kotlin.srcDir(layout.projectDirectory.file("modules/pkl-config-kotlin/examples"))
  }
}

dependencies {
  testImplementation(projects.pklCore)
  testImplementation(projects.pklConfigJava)
  testImplementation(projects.pklConfigKotlin)
  testImplementation(projects.pklCommonsTest)
  testImplementation(libs.junitEngine)
  testImplementation(libs.antlrRuntime)
}

tasks.test {
  inputs.files(fileTree("modules").matching {
    include("**/pages/*.adoc")
  })
}
