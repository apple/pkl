/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  `kotlin-dsl`
  `jvm-toolchains`
}

// Keep this in sync with the constants in `BuildInfo.kt` (those are not addressable here).
val toolchainVersion = 21

dependencies {
  implementation(libs.downloadTaskPlugin)
  implementation(libs.spotlessPlugin)
  implementation(libs.kotlinPlugin) { exclude(module = "kotlin-android-extensions") }
  implementation(libs.shadowPlugin)

  // fix from the Gradle team: makes version catalog symbols available in build scripts
  // see here for more: https://github.com/gradle/gradle/issues/15383
  implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(toolchainVersion)
    vendor = JvmVendorSpec.ADOPTIUM
  }
}

tasks.withType<JavaCompile>().configureEach { options.release = toolchainVersion }

kotlin {
  jvmToolchain(toolchainVersion)
  compilerOptions {
    jvmTarget = JvmTarget.fromTarget(toolchainVersion.toString())
    freeCompilerArgs.add("-Xjdk-release=$toolchainVersion")
  }
}
