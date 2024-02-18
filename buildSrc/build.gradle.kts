import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  java
  `kotlin-dsl`
}

dependencies {
  // prevent other versions of stdlib from creeping in
  implementation(libs.kotlinStdlib)

  implementation(libs.downloadTaskPlugin)
  implementation(libs.spotlessPlugin)
  implementation(libs.testloggerPlugin)
  implementation(libs.detektPlugin)
  implementation(libs.kotlinPluginKover)
  implementation(libs.powerassertPlugin)
  implementation(libs.kotlinPlugin) {
    exclude(module = "kotlin-android-extensions")
  }
  implementation(libs.kotlinPluginSerialization)
  implementation(libs.shadowPlugin)

  // fix from the Gradle team: makes version catalog symbols available in build scripts
  // see here for more: https://github.com/gradle/gradle/issues/15383
  implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

dependencyLocking {
  lockAllConfigurations()
}

// These are toolchain-level settings; for artifact targets, see convention plugins.
private val defaultJvmTarget = "11"
private val defaultKotlinTarget = "1.9"

// Toolchain Kotlin target.
private val kotlinVersion =
  (findProperty("kotlinTarget") as? String ?: defaultKotlinTarget)

// JVM toolchain defaults, properties, and resolved configuration.
private val javaToolchainVersion =
  (findProperty("javaToolchainTarget") as? String ?: defaultJvmTarget)

java {
  sourceCompatibility = JavaVersion.toVersion(javaToolchainVersion)
  targetCompatibility = JavaVersion.toVersion(javaToolchainVersion)

  toolchain {
    languageVersion.set(JavaLanguageVersion.of(javaToolchainVersion))
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.fromTarget(javaToolchainVersion))
    KotlinVersion.fromVersion(kotlinVersion).let {
      apiVersion = it
      languageVersion = it
    }
  }
}
