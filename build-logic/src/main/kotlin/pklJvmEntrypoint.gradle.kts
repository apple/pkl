// https://youtrack.jetbrains.com/issue/KTIJ-19369
@file:Suppress("DSL_SCOPE_VIOLATION")

import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
  java
  application
  kotlin("jvm")
  id("pklJvmLibrary")
  id("pklKotlinLibrary")
  id("pklKotlinTest")
}

// Main build info extension.
val info = the<BuildInfo>()

java {
  JavaVersion.toVersion(info.jvm.entrypoint.target).let {
    sourceCompatibility = it
    targetCompatibility = it
  }
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.fromTarget(info.jvm.entrypoint.target.toString())
  }
}

tasks {
  withType(KotlinJvmCompile::class).configureEach {
    compilerOptions {
      jvmTarget = JvmTarget.fromTarget(info.jvm.entrypoint.target.toString())
    }
  }

  withType<Detekt>().configureEach {
    autoCorrect = info.analysis.autofix
    jvmTarget = info.jvm.lib.target.toString()
  }
}
