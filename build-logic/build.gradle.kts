import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  java
  `kotlin-dsl`
}

description = "Reusable convention plugins for Pkl's build"

val dependencyPins = listOf(
  "commons-io:commons-io" to libs.versions.commonsIo,  // CVE-2021-29425
).toMap()

dependencies {
  // prevent other versions of stdlib from creeping in
  implementation(libs.kotlinStdlib)

  implementation(libs.downloadTaskPlugin)
  implementation(libs.spotlessPlugin)
  implementation(libs.testloggerPlugin)
  implementation(libs.detektPlugin)
  implementation(libs.kotlinPluginKover)
  implementation(libs.powerassertPlugin)
  implementation(libs.shadowPlugin)
  implementation(libs.kotlinPlugin) {
    exclude(module = "kotlin-android-extensions")
  }
  implementation(libs.kotlinPluginSerialization)
  implementation(libs.shadowPlugin)

  // fix from the Gradle team: makes version catalog symbols available in build scripts
  // see here for more: https://github.com/gradle/gradle/issues/15383
  implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

if (properties["lockDependencies"] == "true") dependencyLocking {
  lockAllConfigurations()
}

tasks.jar {
  outputs.cacheIf { true }
}

configurations.all {
  resolutionStrategy {
    eachDependency {
      when (val dep = dependencyPins["${requested.group}:${requested.name}"]) {
        null -> {}
        else -> dep.get().let {
          useVersion(it)
          because("pinned dependencies")
        }
      }
    }
  }
}

// These are toolchain-level settings; for artifact targets, see convention plugins.
private val defaultJvmToolchainTarget = "21"
private val defaultKotlinTarget = "1.9"

// Toolchain Kotlin target.
private val kotlinVersion =
  (findProperty("kotlinTarget") as? String ?: defaultKotlinTarget)

// JVM toolchain defaults, properties, and resolved configuration.
private val javaToolchainVersion =
  (findProperty("javaToolchainTarget") as? String ?: defaultJvmToolchainTarget)

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
