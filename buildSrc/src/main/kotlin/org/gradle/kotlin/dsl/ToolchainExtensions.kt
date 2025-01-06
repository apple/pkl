/*
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.gradle.kotlin.dsl

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.*
import org.gradle.kotlin.dsl.support.serviceOf

private const val oracleGraalvm = true
private val graalvmVendor = if (oracleGraalvm) JvmVendorSpec.ORACLE else JvmVendorSpec.GRAAL_VM

/**
 * Configure a Java toolchain to use a specific version of GraalVM.
 *
 * @param javaVersion The version of GraalVM to use.
 */
fun JavaToolchainSpec.useGraalvm(javaVersion: JavaLanguageVersion) {
  languageVersion = javaVersion
  vendor = graalvmVendor
}

/**
 * Resolve a Java compiler and launcher for the pinned version of GraalVM.
 *
 * @param javaVersion The version of GraalVM to use.
 * @return A pair of Java compiler and launcher.
 */
fun Project.graalvmToolchain(
  javaVersion: JavaLanguageVersion
): Provider<Pair<JavaCompiler, JavaLauncher>> {
  val javaToolchains = serviceOf<JavaToolchainService>()
  val compiler = javaToolchains.compilerFor { useGraalvm(javaVersion) }
  val launcher = javaToolchains.launcherFor { useGraalvm(javaVersion) }
  return provider { compiler.get() to launcher.get() }
}

/**
 * Configure a project to use the pinned version of GraalVM; this will apply the Java compiler and
 * launcher resolved by [graalvmToolchain] to all Java compile and exec tasks.
 */
fun Project.useGraalvmToolchain(javaVersion: JavaLanguageVersion) {
  val (compiler, launcher) = graalvmToolchain(javaVersion).get()
  tasks.withType<JavaCompile> { javaCompiler = compiler }
  tasks.withType<JavaExec> { javaLauncher = launcher }
}

/**
 * Configure a project and Java toolchain to use the pinned version of GraalVM specified within the
 * `libs.versions.toml` file.
 */
fun Project.useGraalVm(javaVersion: JavaLanguageVersion? = null) {
  val libs = the<LibrariesForLibs>()
  val gvmVersion =
    javaVersion
      ?: requireNotNull(libs.versions.graalVmJdkVersion.get().substringBefore('.').toUIntOrNull()) {
          "GraalVmMajor version is not valid or not supported on this platform"
        }
        .let { JavaLanguageVersion.of(it.toInt()) }

  configure<JavaPluginExtension> {
    // assign the Java toolchain to the project
    toolchain { useGraalvm(gvmVersion) }

    // assign toolchain for all configured and applicable tasks
    useGraalvmToolchain(gvmVersion)
  }
}
