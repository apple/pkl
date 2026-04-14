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

dependencies {
  // Align versions of Kotlin modules during dependency resolution.
  // Do NOT align "api", as this would affect consumers' builds.
  implementation(platform(libs.kotlinBom))
  testImplementation(platform(libs.kotlinBom))
}

kotlin {
  compilerOptions {
    val kotlinTarget = KotlinVersion.fromVersion(libs.versions.kotlinTarget.get())
    languageVersion.set(kotlinTarget)
    apiVersion.set(kotlinTarget)
    jvmTarget = JvmTarget.fromTarget(buildInfo.jvmTarget.toString())
    jvmToolchain {
      languageVersion.set(buildInfo.jdkToolchainVersion)
      vendor.set(buildInfo.jdkVendor)
    }
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
