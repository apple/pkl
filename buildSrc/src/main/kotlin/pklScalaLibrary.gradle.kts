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
@file:Suppress("HttpUrlsUsage", "unused")

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.withType

plugins {
  id("pklJavaLibrary")
  scala
}

// Build configuration.
val buildInfo = project.extensions.getByType<BuildInfo>()

// Version Catalog library symbols.
val libs = the<LibrariesForLibs>()

dependencies {
  api(libs.scalaLibrary)
  testImplementation(libs.scalaTestPlusJunit)
  testImplementation(libs.scalaTest)
  testImplementation(libs.diffx)
}

tasks.withType<ScalaCompile>().configureEach {
  scalaCompileOptions.additionalParameters =
    listOf("-Xsource:3", "-release:${buildInfo.jvmTarget}", "-target:${buildInfo.jvmTarget}")
}

tasks.test {
  useJUnitPlatform {
    includeEngines("scalatest")
    testLogging { events("passed", "skipped", "failed") }
  }
}
