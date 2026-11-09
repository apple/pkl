/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

plugins {
  `java-library`
  `jvm-toolchains`
  `jvm-test-suite`
  id("pklKotlinTest")
  id("com.diffplug.spotless")
}

// make sources Jar available to other subprojects
val sourcesJarConfiguration: Provider<Configuration> = configurations.register("sourcesJar")

// Version Catalog library symbols.
val libs = the<LibrariesForLibs>()

// Build configuration.
val info = project.extensions.getByType<BuildInfo>()

java {
  withSourcesJar() // creates `sourcesJar` task
  withJavadocJar()

  toolchain {
    languageVersion = info.jdkToolchainVersion
    vendor = info.jdkVendor
  }
}

artifacts {
  // make sources Jar available to other subprojects
  add("sourcesJar", tasks["sourcesJar"])
}

spotless {
  val revertYearOnlyChanges = RevertYearOnlyChangesStep(rootProject.rootDir, ratchetFrom!!).create()

  java {
    addStep(revertYearOnlyChanges)
    googleJavaFormat(libs.versions.googleJavaFormat.get())
    target("src/*/java/**/*.java")
    licenseHeaderFile(
      rootProject.file("build-logic/src/main/resources/license-header.star-block.txt")
    )
  }
}

tasks.jar {
  manifest {
    attributes +=
      mapOf(
        "Automatic-Module-Name" to "org.${project.name.replace("-", ".")}",
        "Add-Exports" to info.jpmsExportsForJarManifest,
      )
  }
}

tasks.javadoc {
  classpath = sourceSets.main.get().output + sourceSets.main.get().compileClasspath
  source = sourceSets.main.get().allJava
  title = "${project.name} ${project.version} API"
  (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

val truffleJavacArgs =
  listOf(
    // TODO: determine correct limits for Truffle specializations
    // (see https://graalvm.slack.com/archives/CNQSB2DHD/p1712380902746829)
    "-Atruffle.dsl.SuppressWarnings=truffle-limit"
  )

tasks.compileJava {
  javaCompiler = info.javaCompiler
  options.compilerArgs.addAll(truffleJavacArgs + info.jpmsAddModulesFlags)
}

tasks.withType<JavaCompile>().configureEach {
  javaCompiler = info.javaCompiler
  options.release = info.jvmTarget
}

tasks.withType<JavaExec>().configureEach { jvmArgs(info.jpmsAddModulesFlags) }

fun Test.configureJdkTestTask(launcher: Provider<JavaLauncher>) {
  useJUnitPlatform()
  javaLauncher = launcher
  systemProperties.putAll(info.testProperties)
  jvmArgs.addAll(info.jpmsAddModulesFlags)
}

tasks.test { configureJdkTestTask(info.javaTestLauncher) }

// Prepare test tasks for each JDK version which is within the test target suite for Pkl. Each task
// uses a pinned JDK toolchain version, and is named for the major version which is tested.
//
// Test tasks configured in this manner are executed manually by name, e.g. `./gradlew testJdk11`,
// and automatically as dependencies of `check`.
//
// We omit the current JDK from this list because it is already tested, in effect, by the default
// `test` task.
//
// Pkl subprojects may elect to further configure these tasks as needed; by default, each task
// inherits the configuration of the default `test` task (aside from an overridden launcher).
val jdkTestTasks = info.multiJdkTestingWith(tasks.test) { (_, jdk) -> configureJdkTestTask(jdk) }

tasks.check { dependsOn(jdkTestTasks) }
