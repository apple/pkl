/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.compile.JavaCompile

plugins {
  `java-library`
  id("net.ltgt.errorprone")
  id("net.ltgt.nullaway")
}

val libs = the<LibrariesForLibs>()

dependencies {
  api(libs.jspecify)
  errorprone(libs.errorProne)
  errorprone(libs.nullaway)
}

nullaway { onlyNullMarked = true }

tasks.withType<JavaCompile>().configureEach {
  options.errorprone.disableAllChecks = true
  options.errorprone.nullaway {
    error()
    onlyNullMarked = true
    jspecifyMode = true
    // honor assert x != null in addition to Objects.requireNonNull(x)
    assertsEnabled = true
  }
}
