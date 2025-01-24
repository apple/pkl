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
import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
  id("pklJavaLibrary")

  kotlin("jvm")
}

val buildInfo = project.extensions.getByType<BuildInfo>()

// Version Catalog library symbols.
val libs = the<LibrariesForLibs>()

dependencies {
  // At least some of our kotlin APIs contain Kotlin stdlib types
  // that aren't compiled away by kotlinc (e.g., `kotlin.Function`).
  // So let's be conservative and default to `api` for now.
  // For Kotlin APIs that only target Kotlin users (e.g., pkl-config-kotlin),
  // it won't make a difference.
  api(buildInfo.libs.findLibrary("kotlinStdLib").get())
}

tasks.compileKotlin {
  enabled = true // disabled by pklJavaLibrary
}

tasks.withType<KotlinJvmCompile>().configureEach {
  compilerOptions {
    // must match the minimum JVM bytecode target for Pkl
    jvmTarget = JvmTarget.JVM_17

    // enable java parameter names for stronger Kotlin-Java interop and debugging
    javaParameters = true

    // @TODO: flip to `true` once compiler warnings are all fixed
    allWarningsAsErrors = false

    freeCompilerArgs.addAll(
      listOf(
        // enable strict nullability checking and integration with pkl's own annotations
        "-Xjsr305=strict",
        "-Xjsr305=@org.pkl.core.util.Nullable:strict",
        "-Xjsr305=@org.pkl.core.util.Nonnull:strict",
      )
    )
  }
}
