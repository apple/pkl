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
import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway
import org.apache.tools.ant.filters.ReplaceTokens

plugins {
  kotlin("jvm") // for `src/generator/kotlin`
  id("pklAllProjects")
  id("pklJavaLibrary")
  id("pklPublishLibrary")
  id("pklNativeLifecycle")
  id("pklJSpecify")
  idea
}

val generatorSourceSet: NamedDomainObjectProvider<SourceSet> = sourceSets.register("generator")

val externalReaderFixtureSourceSet: NamedDomainObjectProvider<SourceSet> =
  sourceSets.register("externalReaderFixture") {
    compileClasspath += sourceSets.test.get().output + sourceSets.test.get().compileClasspath
    runtimeClasspath += sourceSets.test.get().output + sourceSets.test.get().runtimeClasspath
  }

val externalReaderFixtureImplementation: Configuration =
  configurations.getByName("externalReaderFixtureImplementation") {
    extendsFrom(configurations.testImplementation.get())
  }

idea {
  module {
    // mark generated/truffle as generated source dir
    sourceDirs = sourceDirs + files("generated/truffle")
    generatedSourceDirs = generatedSourceDirs + files("generated/truffle")
  }
}

val javaExecutableConfiguration: Configuration = configurations.create("javaExecutable")

dependencies {
  annotationProcessor(libs.truffleDslProcessor)
  annotationProcessor(generatorSourceSet.get().runtimeClasspath)

  compileOnly(libs.errorProneAnnotations)
  // pkl-core implements pkl-executor's ExecutorSpi, but the SPI doesn't ship with pkl-core
  compileOnly(projects.pklExecutor)

  implementation(projects.pklParser)
  implementation(projects.pklFormatter)
  implementation(libs.msgpack)
  implementation(libs.truffleApi)
  implementation(libs.graalSdk)

  implementation(libs.paguro) { exclude(group = "org.jetbrains", module = "annotations") }

  implementation(libs.snakeYaml)

  testImplementation(projects.pklCommonsTest)
  testImplementation(libs.wiremock)

  add("generatorImplementation", libs.javaPoet)
  add("generatorImplementation", libs.truffleApi)
  add("generatorImplementation", libs.jspecify)
  add("generatorImplementation", projects.pklParser)

  javaExecutableConfiguration(project(":pkl-cli", "javaExecutable"))
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-core")
        description.set(
          """
          Core implementation of the Pkl configuration language.
          Includes Java APIs for embedding the language into JVM applications,
          and for building libraries and tools on top of the language.
          """
            .trimIndent()
        )
      }
    }
  }
}

tasks.processResources {
  inputs.property("version", buildInfo.pklVersion)
  inputs.property("commitId", buildInfo.commitId)

  filesMatching("org/pkl/core/Release.properties") {
    val stdlibModules =
      fileTree("$rootDir/stdlib") {
          include("*.pkl")
          exclude("doc-package-info.pkl")
        }
        .map { "pkl:" + it.nameWithoutExtension }
        .sortedBy { it.lowercase() }

    filter<ReplaceTokens>(
      "tokens" to
        mapOf(
          "version" to buildInfo.pklVersion,
          "commitId" to buildInfo.commitId,
          "stdlibModules" to stdlibModules.joinToString(","),
        )
    )
  }

  into("org/pkl/core/stdlib") { from("$rootDir/stdlib") { include("*.pkl") } }
}

tasks.compileJava { options.generatedSourceOutputDirectory.set(file("generated/truffle")) }

tasks.withType<JavaCompile>().configureEach {
  options.errorprone.nullaway {
    // Do not require LateInit fields to be initialized at construction time.
    // Unfortunately, IntelliJ doesn't currently understand this,
    // and therefore emits many warnings related to LateInit.
    excludedFieldAnnotations.add("org.pkl.core.util.LateInit")

    // For now, don't analyze code that deals with Truffle ASTs.
    unannotatedSubPackages.addAll("org.pkl.core.ast", "org.pkl.core.stdlib")
  }
}

tasks.compileKotlin { enabled = false }

val externalReaderFixture by tasks.registering {
  group = "build"
  dependsOn(tasks.named("compileExternalReaderFixtureJava"))
  inputs.files(externalReaderFixtureSourceSet.map { it.output })
  val fileName = if (buildInfo.os.isWindows) "externalreader.bat" else "externalreader"
  val outputFile = layout.buildDirectory.file("fixtures/$fileName")
  outputs.file(outputFile)
  doLast {
    val classpath = externalReaderFixtureSourceSet.get().runtimeClasspath.asPath
    val scriptContent =
      if (buildInfo.os.isWindows) {
        """
          @echo off
          java -cp $classpath org.pkl.core.externalreaderfixture.Main
        """
          .trimIndent()
      } else {
        """
          #!/usr/bin/env bash

          java -cp $classpath org.pkl.core.externalreaderfixture.Main
        """
          .trimIndent()
      }

    outputFile.get().asFile.writeText(scriptContent)
    outputFile.get().asFile.setExecutable(true)
    println("Created external reader ${outputFile.get().asFile.absolutePath}")
  }
}

tasks.test {
  configureTest()
  dependsOn(externalReaderFixture)
  environment(
    "PATH",
    listOf(System.getenv("PATH"), layout.buildDirectory.dir("fixtures/").get())
      .joinToString(File.pathSeparator),
  )
  useJUnitPlatform {
    excludeEngines("MacAarch64LanguageSnippetTestsEngine")
    excludeEngines("LinuxAmd64LanguageSnippetTestsEngine")
    excludeEngines("LinuxAarch64LanguageSnippetTestsEngine")
    excludeEngines("AlpineLanguageSnippetTestsEngine")
    excludeEngines("WindowsLanguageSnippetTestsEngine")
  }

  // testing very large lists requires more memory than the default 512m!
  maxHeapSize = "1g"

  systemProperty(
    "org.pkl.core.testExternalReaderPath",
    externalReaderFixture.map { it.outputs.files.singleFile.absolutePath },
  )
}

val generateBaseModuleMembers by
  tasks.registering(JavaExec::class) {
    group = "build"

    val outputDir = layout.buildDirectory.dir("generated/sources/baseModuleMembers")

    val basePklFile = layout.projectDirectory.file("../stdlib/base.pkl")

    inputs
      .file(basePklFile)
      .withPropertyName("basePkl")
      .withPathSensitivity(PathSensitivity.RELATIVE)

    outputs.dir(outputDir)

    classpath =
      generatorSourceSet.get().runtimeClasspath + tasks.processResources.get().outputs.files
    mainClass = "org.pkl.core.generator.BaseModuleMembersGenerator"

    argumentProviders.add(
      CommandLineArgumentProvider {
        listOf(basePklFile.asFile.absolutePath, outputDir.get().asFile.absolutePath)
      }
    )
  }

sourceSets.main { java.srcDir(layout.buildDirectory.dir("generated/sources/baseModuleMembers")) }

tasks.compileJava { dependsOn(generateBaseModuleMembers) }

tasks.sourcesJar { dependsOn(generateBaseModuleMembers) }

val testJavaExecutable by
  tasks.registering(Test::class) {
    configureExecutableTest("LanguageSnippetTestsEngine")
    classpath =
      // compiled test classes
      sourceSets.test.get().output +
        // java executable
        javaExecutableConfiguration +
        // test-only dependencies
        // (test dependencies that are also main dependencies must already be contained in java
        // executable;
        // to verify that we don't want to include them here)
        (configurations.testRuntimeClasspath.get() - configurations.runtimeClasspath.get())

    // testing very large lists requires more memory than the default 512m!
    maxHeapSize = "1g"
  }

tasks.check { dependsOn(testJavaExecutable) }

val testMacExecutableAarch64 by
  tasks.registering(Test::class) {
    dependsOn(":pkl-cli:macExecutableAarch64")
    configureExecutableTest("MacAarch64LanguageSnippetTestsEngine")
  }

val testLinuxExecutableAmd64 by
  tasks.registering(Test::class) {
    dependsOn(":pkl-cli:linuxExecutableAmd64")
    configureExecutableTest("LinuxAmd64LanguageSnippetTestsEngine")
  }

val testLinuxExecutableAarch64 by
  tasks.registering(Test::class) {
    dependsOn(":pkl-cli:linuxExecutableAarch64")
    configureExecutableTest("LinuxAarch64LanguageSnippetTestsEngine")
  }

val testAlpineExecutableAmd64 by
  tasks.registering(Test::class) {
    dependsOn(":pkl-cli:alpineExecutableAmd64")
    configureExecutableTest("AlpineLanguageSnippetTestsEngine")
  }

val testWindowsExecutableAmd64 by
  tasks.registering(Test::class) {
    dependsOn(":pkl-cli:windowsExecutableAmd64")
    configureExecutableTest("WindowsLanguageSnippetTestsEngine")
  }

tasks.testNativeMacOsAarch64 { dependsOn(testMacExecutableAarch64) }

tasks.testNativeLinuxAarch64 { dependsOn(testLinuxExecutableAarch64) }

tasks.testNativeLinuxAmd64 { dependsOn(testLinuxExecutableAmd64) }

tasks.testNativeAlpineLinuxAmd64 { dependsOn(testAlpineExecutableAmd64) }

tasks.testNativeWindowsAmd64 { dependsOn(testWindowsExecutableAmd64) }

tasks.clean {
  delete("generated/")
  delete(layout.buildDirectory.dir("test-packages"))
}

private fun Test.configureTest() {
  inputs
    .dir("src/test/files/LanguageSnippetTests/input")
    .withPropertyName("languageSnippetTestsInput")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  inputs
    .dir("src/test/files/LanguageSnippetTests/input-helper")
    .withPropertyName("languageSnippetTestsInputHelper")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  inputs
    .dir("src/test/files/LanguageSnippetTests/output")
    .withPropertyName("languageSnippetTestsOutput")
    .withPathSensitivity(PathSensitivity.RELATIVE)
}

private fun Test.configureExecutableTest(engineName: String) {
  configureTest()
  testClassesDirs = files(tasks.test.get().testClassesDirs)
  classpath = tasks.test.get().classpath
  useJUnitPlatform { includeEngines(engineName) }
}
