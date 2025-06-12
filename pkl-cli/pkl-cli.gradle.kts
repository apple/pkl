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
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import org.gradle.kotlin.dsl.support.serviceOf

plugins {
  pklAllProjects
  pklKotlinLibrary
  pklPublishLibrary
  pklJavaExecutable
  pklNativeExecutable
  `maven-publish`

  // already on build script class path (see buildSrc/build.gradle.kts),
  // hence must only specify plugin ID here
  id(libs.plugins.shadow.get().pluginId)

  alias(libs.plugins.checksum)
}

// make Java executable available to other subprojects
val javaExecutableConfiguration: Configuration = configurations.create("javaExecutable")

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-cli")
        description.set("Pkl CLI Java library.")
      }
    }
  }
}

dependencies {
  implementation(libs.truffleRuntime)
  compileOnly(libs.graalSdk)

  // CliEvaluator exposes PClass
  api(projects.pklCore)
  // CliEvaluatorOptions exposes CliBaseOptions
  api(projects.pklCommonsCli)

  implementation(projects.pklCommons)
  implementation(libs.jansi)
  implementation(libs.jlineReader)
  implementation(libs.jlineTerminal)
  implementation(libs.jlineTerminalJansi)
  implementation(projects.pklServer)
  implementation(libs.clikt)

  testImplementation(projects.pklCommonsTest)
  testImplementation(libs.wiremock)
}

tasks.jar {
  manifest.attributes +=
    mapOf("Main-Class" to "org.pkl.cli.Main", "Add-Exports" to buildInfo.jpmsExportsForJarManifest)
}

tasks.javadoc { enabled = false }

tasks.shadowJar {
  archiveFileName.set("jpkl")

  exclude("META-INF/maven/**")
  exclude("META-INF/upgrade/**")

  exclude("module-info.*")
}

val testJavaExecutable by
  tasks.registering(Test::class) {
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath =
      // compiled test classes
      sourceSets.test.get().output +
        // java executable
        tasks.javaExecutable.get().outputs.files +
        // test-only dependencies
        // (test dependencies that are also main dependencies must already be contained in java
        // executable;
        // to verify that, we don't want to include them here)
        (configurations.testRuntimeClasspath.get() - configurations.runtimeClasspath.get())
  }

// Setup `testJavaExecutable` tasks for multi-JDK testing.
val testJavaExecutableOnOtherJdks = buildInfo.multiJdkTestingWith(testJavaExecutable)

// Prepare a run of the fat JAR, optionally with a specific Java launcher.
private fun setupJavaExecutableRun(
  name: String,
  args: Array<String>,
  launcher: Provider<JavaLauncher>? = null,
  configurator: Exec.() -> Unit = {},
) =
  tasks.register(name, Exec::class) {
    dependsOn(tasks.javaExecutable)
    val outputFile = layout.buildDirectory.file(name) // dummy output to satisfy up-to-date check
    outputs.file(outputFile)

    executable =
      when (launcher) {
        null -> "java"
        else -> launcher.get().executablePath.asFile.absolutePath
      }
    standardOutput = OutputStream.nullOutputStream()

    args("-jar", tasks.javaExecutable.get().outputs.files.singleFile.toString(), *args)

    doFirst { outputFile.get().asFile.delete() }

    doLast { outputFile.get().asFile.writeText("OK") }

    configurator()
  }

val evalTestFlags = arrayOf("eval", "-x", "1 + 1", "pkl:base")

fun Exec.useRootDirAndSuppressOutput() {
  workingDir = rootProject.layout.projectDirectory.asFile
  standardOutput = ByteArrayOutputStream() // we only care that this exec doesn't fail
}

// 0.28 Preparing for JDK21 toolchains revealed that `testStartJavaExecutable` may pass, even though
// the evaluator fails. To catch this, we need to test the evaluator. We render the CircleCI config
// as a realistic test of the fat JAR.
val testEvalJavaExecutable by
  setupJavaExecutableRun("testEvalJavaExecutable", evalTestFlags) { useRootDirAndSuppressOutput() }

// Run the same evaluator tests on all configured JDK test versions.
val testEvalJavaExecutableOnOtherJdks =
  buildInfo.jdkTestRange.map { jdkTarget ->
    setupJavaExecutableRun(
      "testEvalJavaExecutableJdk${jdkTarget.asInt()}",
      evalTestFlags,
      serviceOf<JavaToolchainService>().launcherFor { languageVersion = jdkTarget },
    ) {
      useRootDirAndSuppressOutput()
    }
  }

tasks.check {
  dependsOn(
    testJavaExecutable,
    testJavaExecutableOnOtherJdks,
    testEvalJavaExecutable,
    testEvalJavaExecutableOnOtherJdks,
  )
}

tasks.checkNative {
  dependsOn(":pkl-core:checkNative")
  dependsOn(":pkl-server:checkNative")
}

executable {
  name = "pkl"
  javaName = "jpkl"
  documentationName = "Pkl CLI"
  publicationName = "pkl-cli"
  javaPublicationName = "pkl-cli-java"
  mainClass = "org.pkl.cli.Main"
  website = "https://pkl-lang.org/main/current/pkl-cli/index.html"
}

// make Java executable available to other subprojects
// (we don't do the same for native executables because we don't want tasks assemble/build to build
// them)
artifacts {
  add("javaExecutable", tasks.javaExecutable.map { it.outputs.files.singleFile }) {
    name = "pkl-cli-java"
    classifier = null
    extension = "jar"
    builtBy(tasks.javaExecutable)
  }
}
