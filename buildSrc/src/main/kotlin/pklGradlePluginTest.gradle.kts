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
plugins { java }

val gradlePluginTests = extensions.create<GradlePluginTests>("gradlePluginTests")

tasks.addRule("Pattern: compatibilityTest[All|Releases|Latest|Candidate|Nightly|<GradleVersion>]") {
  val taskName = this
  val matchResult = Regex("compatibilityTest(.+)").matchEntire(taskName) ?: return@addRule

  when (val taskNameSuffix = matchResult.groupValues[1]) {
    "All" ->
      task("compatibilityTestAll") {
        dependsOn(
          "compatibilityTestReleases",
          "compatibilityTestCandidate",
          "compatibilityTestNightly",
        )
      }
    // releases in configured range
    "Releases" ->
      task("compatibilityTestReleases") {
        val versionInfos = GradleVersionInfo.fetchReleases()
        val allVersions =
          versionInfos
            .filter { versionInfo ->
              val v = versionInfo.gradleVersion
              !versionInfo.broken &&
                v in gradlePluginTests.minGradleVersion..gradlePluginTests.maxGradleVersion &&
                v !in gradlePluginTests.skippedGradleVersions
            }
            .sortedBy { it.gradleVersion }
        val versionsToTestAgainst = listOf(allVersions.first(), allVersions.last())

        dependsOn(versionsToTestAgainst.map { createCompatibilityTestTask(it) })
      }
    // latest release (if not developing against latest)
    "Latest" ->
      task("compatibilityTestLatest") {
        val versionInfo = GradleVersionInfo.fetchCurrent()
        if (versionInfo.version == gradle.gradleVersion) {
          doLast {
            println(
              "No new Gradle release available. " +
                "(Run `gradlew test` to test against ${versionInfo.version}.)"
            )
          }
        } else {
          dependsOn(createCompatibilityTestTask(versionInfo))
        }
      }
    // active release candidate (if any)
    "Candidate" ->
      task("compatibilityTestCandidate") {
        val versionInfo = GradleVersionInfo.fetchRc()
        if (versionInfo?.activeRc == true) {
          dependsOn(createCompatibilityTestTask(versionInfo))
        } else {
          doLast { println("No active Gradle release candidate available.") }
        }
      }
    // latest nightly
    "Nightly" ->
      task("compatibilityTestNightly") {
        val versionInfo = GradleVersionInfo.fetchNightly()
        dependsOn(createCompatibilityTestTask(versionInfo))
      }
    // explicit version
    else ->
      createCompatibilityTestTask(
        taskNameSuffix,
        "https://services.gradle.org/distributions-snapshots/gradle-$taskNameSuffix-bin.zip",
      )
  }
}

fun createCompatibilityTestTask(versionInfo: GradleVersionInfo): Task =
  createCompatibilityTestTask(versionInfo.version, versionInfo.downloadUrl)

fun createCompatibilityTestTask(version: String, downloadUrl: String): Task {
  return tasks.create("compatibilityTest$version", Test::class.java) {
    mustRunAfter(tasks.test)

    maxHeapSize = tasks.test.get().maxHeapSize
    jvmArgs = tasks.test.get().jvmArgs
    classpath = tasks.test.get().classpath
    systemProperty("testGradleVersion", version)
    systemProperty("testGradleDistributionUrl", downloadUrl)

    doFirst {
      if (version == gradle.gradleVersion && gradle.taskGraph.hasTask(tasks.test.get())) {
        // don't test same version twice
        println("This version has already been tested by the `test` task.")
        throw StopExecutionException()
      }
    }
  }
}
