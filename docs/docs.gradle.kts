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
      srcDir(file("modules/java-binding/examples"))
    }
    val kotlin = project.extensions
      .getByType<KotlinJvmProjectExtension>()
      .sourceSets[name]
      .kotlin
    kotlin.srcDir(file("modules/kotlin-binding/examples"))
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
  }).withPropertyName("asciiDocFiles").withPathSensitivity(PathSensitivity.RELATIVE)
}
