/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.GradleException
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  java
  kotlin("jvm")
  id("com.diffplug.spotless")
}

val buildInfo = project.extensions.getByType<BuildInfo>()

val libs = the<LibrariesForLibs>()

kotlin {
  jvmToolchain {
    languageVersion.set(buildInfo.jdkToolchainVersion)
    vendor.set(buildInfo.jdkVendor)
  }
  compilerOptions {
    val kotlinTarget = KotlinVersion.fromVersion(libs.versions.kotlinTarget.get())
    languageVersion.set(kotlinTarget)
    apiVersion.set(kotlinTarget)
    jvmTarget = JvmTarget.fromTarget(buildInfo.jvmTarget.toString())
    freeCompilerArgs.addAll(
      "-jvm-default=no-compatibility", // was: -Xjvm-default=all
      "-Xjdk-release=${buildInfo.jvmTarget}",
      "-Xjsr305=strict",
    )
  }
}

spotless {
  val revertYearOnlyChanges = RevertYearOnlyChangesStep(rootProject.rootDir, ratchetFrom!!).create()

  kotlin {
    addStep(revertYearOnlyChanges)
    ktfmt(libs.versions.ktfmt.get()).googleStyle()
    target("src/*/kotlin/**/*.kt")
    licenseHeaderFile(rootProject.file("buildSrc/src/main/resources/license-header.star-block.txt"))
  }
}

/**
 * Kotlin modules to guard: fail the build if any dependency resolves to a version higher than
 * `libs.versions.kotlinTarget`. This includes versions introduced via direct declarations, BOMs,
 * version catalogs, or constraints.
 */
val guardedKotlinModules = setOf(libs.kotlinStdLib.get().module, libs.kotlinReflect.get().module)

/**
 * Classpath configurations where the above rule applies. Kept narrow to avoid interfering with
 * Gradle/Kotlin plugin internal configurations.
 */
val guardedConfigurations =
  setOf(
    configurations.compileClasspath,
    configurations.runtimeClasspath,
    configurations.testCompileClasspath,
    configurations.testRuntimeClasspath,
  )

guardedConfigurations.forEach { configuration ->
  configuration.configure {
    incoming.afterResolve {
      resolutionResult.allComponents.forEach { component ->
        val moduleVersion = component.moduleVersion ?: return@forEach
        if (
          moduleVersion.module in guardedKotlinModules &&
            moduleVersion.version.exceedsKotlinTarget()
        ) {
          throw GradleException(
            "Resolved ${moduleVersion.module}:${moduleVersion.version} on configuration $name, " +
              "which exceeds the allowed Kotlin version ($kotlinTargetVersion)"
          )
        }
      }
    }
  }
}

// also works for version ranges like: [2.3.0,)
val kotlinVersionRegex = Regex("""(\d+)\.(\d+)(?:\.\d+)?""")
val kotlinTargetVersion = libs.versions.kotlinTarget.get()
val targetMajor = kotlinTargetVersion.substringBefore('.').toInt()
val targetMinor = kotlinTargetVersion.substringAfter('.').toInt()

fun String.exceedsKotlinTarget(): Boolean {
  val version =
    kotlinVersionRegex.find(this) ?: throw GradleException("Could not parse Kotlin version: $this")
  val major = version.groupValues[1].toInt()
  val minor = version.groupValues[2].toInt()
  return major > targetMajor || (major == targetMajor && minor > targetMinor)
}
