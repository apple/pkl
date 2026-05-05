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
import com.diffplug.gradle.spotless.KotlinGradleExtension
import org.gradle.accessors.dm.LibrariesForLibs

plugins { id("com.diffplug.spotless") }

val buildInfo = extensions.create<BuildInfo>("buildInfo", project)

configurations {
  val rejectedVersionSuffix = Regex("-alpha|-beta|-eap|-m|-rc|-snapshot", RegexOption.IGNORE_CASE)
  val versionSuffixRejectionExemptions =
    setOf(
      // I know.
      // This looks odd.
      // But yes, it's transitively required by one of the release versions of `zinc`
      // https://github.com/sbt/zinc/blame/57a2df7104b3ce27b46404bb09a0126bd4013427/project/Dependencies.scala#L85
      "com.eed3si9n:shaded-scalajson_2.13:1.0.0-M4"
    )
  configureEach {
    resolutionStrategy {
      // forbid dependencies whose pom.xml's include version ranges, because this will lead to
      // unreproducible builds.

      failOnDynamicVersions()
      componentSelection {
        all {
          if (
            rejectedVersionSuffix.containsMatchIn(candidate.version) &&
              !versionSuffixRejectionExemptions.contains(
                "${candidate.group}:${candidate.module}:${candidate.version}"
              )
          ) {
            reject(
              "Rejected dependency $candidate " +
                "because it has a prelease version suffix matching `$rejectedVersionSuffix`."
            )
          }
        }
      }
    }
  }
}

tasks.withType<JavaCompile>().configureEach {
  javaCompiler = buildInfo.javaCompiler
  options.release = buildInfo.jvmTarget
}

plugins.withType(IdeaPlugin::class).configureEach {
  val errorMessage =
    "Use IntelliJ Gradle import instead of running the `idea` task. See README for more information."

  tasks.named("idea") { doFirst { throw GradleException(errorMessage) } }
  tasks.named("ideaModule") { doFirst { throw GradleException(errorMessage) } }
  if (project == rootProject) {
    tasks.named("ideaProject") { doFirst { throw GradleException(errorMessage) } }
  }
}

plugins.withType(MavenPublishPlugin::class).configureEach {
  configure<PublishingExtension> {
    // CI builds pick up artifacts from this repo.
    // It's important that this repo is only declared once per project.
    repositories {
      maven {
        name = "projectLocal" // affects task names
        url = rootDir.resolve("build/m2").toURI()
      }
    }
    // use resolved/locked (e.g., `1.15`)
    // instead of declared (e.g., `1.+`)
    // dependency versions in generated POMs
    publications {
      withType(MavenPublication::class.java) {
        versionMapping { allVariants { fromResolutionResult() } }
      }
    }
  }
}

val allDependencies by tasks.registering(DependencyReportTask::class)

tasks.withType(Test::class).configureEach {
  System.getProperty("testReportsDir")?.let { reportsDir ->
    reports.junitXml.outputLocation.set(file(reportsDir).resolve(project.name).resolve(name))
  }
  debugOptions {
    enabled = System.getProperty("jvmdebug")?.toBoolean() ?: false
    host = "*"
    port = 5005
    suspend = true
    server = true
  }
}

tasks.withType(JavaExec::class).configureEach {
  debugOptions {
    enabled = System.getProperty("jvmdebug")?.toBoolean() ?: false
    host = "*"
    port = 5005
    suspend = true
    server = true
  }
}

// Version Catalog library symbols.
private val libs = the<LibrariesForLibs>()

private val licenseHeaderFile by lazy {
  rootProject.file("build-logic/src/main/resources/license-header.star-block.txt")
}

private fun KotlinGradleExtension.configureFormatter() {
  ktfmt(libs.versions.ktfmt.get()).googleStyle()
  licenseHeaderFile(licenseHeaderFile, "([a-zA-Z]|@file|//)")
}

val originalRemoteName = System.getenv("PKL_ORIGINAL_REMOTE_NAME") ?: "origin"
// if we're running against a release branch (or a PR targeted at one), use that branch for
// ratcheting
// these env vars are set by GitHub actions:
// https://docs.github.com/en/actions/reference/workflows-and-actions/variables#default-environment-variables
val ratchetBranchName =
  (System.getenv("GITHUB_BASE_REF") ?: System.getenv("GITHUB_REF_NAME"))?.let {
    if (it.startsWith("release/")) it else null
  } ?: "main"

spotless {
  ratchetFrom = "$originalRemoteName/$ratchetBranchName"

  val revertYearOnlyChangesStep =
    RevertYearOnlyChangesStep(rootProject.rootDir, ratchetFrom!!).create()

  // When building root project, format build-logic files too.
  // We need this because build-logic is not a subproject of the root project, so a top-level
  // `spotlessApply` will not trigger `build-logic:spotlessApply`.
  if (project.path == rootProject.path) {
    kotlinGradle {
      configureFormatter()
      addStep(revertYearOnlyChangesStep)
      target("*.kts", "build-logic/*.kts", "build-logic/src/*/kotlin/**/*.kts")
    }
    kotlin {
      ktfmt(libs.versions.ktfmt.get()).googleStyle()
      target("build-logic/src/*/kotlin/**/*.kt")
      licenseHeaderFile(licenseHeaderFile)
      addStep(revertYearOnlyChangesStep)
    }
  } else {
    kotlinGradle {
      configureFormatter()
      addStep(revertYearOnlyChangesStep)
      target("*.kts")
    }
  }
}
