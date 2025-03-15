/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.gradle.kotlin.dsl.support.serviceOf

plugins {
  id("pklJavaLibrary")
  id("pklPublishLibrary")
  id("com.github.johnrengelman.shadow")
}

val executableSpec = project.extensions.create("executable", ExecutableSpec::class.java)
val buildInfo = project.extensions.getByType<BuildInfo>()

val javaExecutable by
  tasks.registering(ExecutableJar::class) {
    group = "build"
    dependsOn(tasks.jar)
    inJar = tasks.shadowJar.flatMap { it.archiveFile }
    val effectiveJavaName =
      executableSpec.javaName.map { name -> if (buildInfo.os.isWindows) "$name.bat" else name }
    outJar = layout.buildDirectory.dir("executable").flatMap { it.file(effectiveJavaName) }

    // uncomment for debugging
    // jvmArgs.addAll("-ea", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
  }

fun Task.setupTestStartJavaExecutable(launcher: Provider<JavaLauncher>? = null) {
  group = "verification"
  dependsOn(javaExecutable)

  // dummy output to satisfy up-to-date check
  val outputFile = layout.buildDirectory.file("testStartJavaExecutable/$name")
  outputs.file(outputFile)

  val execOutput =
    providers.exec {
      val executablePath = javaExecutable.get().outputs.files.singleFile
      if (launcher?.isPresent == true) {
        commandLine(
          launcher.get().executablePath.asFile.absolutePath,
          "-jar",
          executablePath.absolutePath,
          "--version",
        )
      } else {
        commandLine(executablePath.absolutePath, "--version")
      }
    }

  doLast {
    val outputText = execOutput.standardOutput.asText.get()
    if (!outputText.contains(buildInfo.pklVersionNonUnique)) {
      throw GradleException(
        "Expected version output to contain current version (${buildInfo.pklVersionNonUnique}), but got '$outputText'"
      )
    }
    outputFile.get().asFile.toPath().apply {
      try {
        parent.createDirectories()
      } catch (ignored: java.nio.file.FileAlreadyExistsException) {}
      writeText("OK")
    }
  }
}

val testStartJavaExecutable by tasks.registering { setupTestStartJavaExecutable() }

// Setup `testStartJavaExecutable` tasks for multi-JDK testing.
val testStartJavaExecutableOnOtherJdks =
  buildInfo.jdkTestRange.map { jdkTarget ->
    tasks.register("testStartJavaExecutableJdk${jdkTarget.asInt()}") {
      val toolChainService: JavaToolchainService = serviceOf()
      val launcher = toolChainService.launcherFor { languageVersion = jdkTarget }
      setupTestStartJavaExecutable(launcher)
    }
  }

tasks.assemble { dependsOn(javaExecutable) }

tasks.check {
  dependsOn(testStartJavaExecutable)
  if (buildInfo.multiJdkTesting) {
    dependsOn(testStartJavaExecutableOnOtherJdks)
  }
}

publishing {
  publications {
    // need to put in `afterEvaluate` because `artifactId` cannot be set lazily.
    project.afterEvaluate {
      register<MavenPublication>("javaExecutable") {
        artifactId = executableSpec.javaPublicationName.get()

        artifact(javaExecutable.map { it.outputs.files.singleFile }) {
          classifier = null
          extension = "jar"
          builtBy(javaExecutable)
        }

        pom {
          url = executableSpec.website
          description =
            executableSpec.documentationName.map { name ->
              """
            $name executable for Java.
            Can be executed directly, or with `java -jar <path/to/jpkl>`.
            Requires Java 17 or higher.
            """
                .trimIndent()
            }
        }
      }
    }
  }
}

signing { project.afterEvaluate { sign(publishing.publications["javaExecutable"]) } }
