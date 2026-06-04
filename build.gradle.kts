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
// https://youtrack.jetbrains.com/issue/KTIJ-19369
import org.jetbrains.gradle.ext.ActionDelegationConfig
import org.jetbrains.gradle.ext.ActionDelegationConfig.TestRunner.PLATFORM
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.taskTriggers

plugins {
  id("pklAllProjects")
  id("pklGraalVm")

  alias(libs.plugins.ideaExt)
  alias(libs.plugins.jmh) apply false
  alias(libs.plugins.nexusPublish)
}

nexusPublishing {
  repositories {
    sonatype {
      nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
      snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
    }
  }
}

val configureLateInitAnnotation by tasks.registering(ConfigureLateInitAnnotation::class)

idea {
  project {
    this as ExtensionAware
    configure<ProjectSettings> {
      this as ExtensionAware
      configure<ActionDelegationConfig> {
        delegateBuildRunToGradle = true
        testRunner = PLATFORM
      }
      taskTriggers.afterSync(configureLateInitAnnotation)
    }
  }
}

val clean by tasks.existing {
  val buildDirectory = layout.buildDirectory
  doLast { delete(buildDirectory) }
}

val printVersion by tasks.registering {
  val pklVersion = buildInfo.pklVersion
  doFirst { println(pklVersion.get()) }
}

val printInfo by tasks.registering {
  val arch = buildInfo.arch
  val pklVersion = buildInfo.pklVersion
  val pklVersionNonUnique = buildInfo.pklVersionNonUnique
  val commitId = buildInfo.commitId
  val gradleVerison = gradle.gradleVersion
  val javaVersion = System.getProperty("java.version")
  val isParallel = gradle.startParameter.isParallelProjectExecutionEnabled
  val maxWorkerCount = gradle.startParameter.maxWorkerCount
  val projectVersion = project.version
  doFirst {
    val message =
      """
      ====
      Gradle version : $gradleVerison
      Java version   : $javaVersion
      isParallel     : $isParallel
      maxWorkerCount : $maxWorkerCount
      Architecture   : $arch

      Project Version        : $projectVersion
      Pkl Version            : ${pklVersion.get()}
      Pkl Non-Unique Version : $pklVersionNonUnique
      Git Commit ID          : ${commitId.get()}
      ====
      """
        .trimIndent()

    val formattedMessage =
      message.replace("====", "=".repeat(message.lines().maxByOrNull { it.length }!!.length))

    println(formattedMessage)
  }
}
