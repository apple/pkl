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
import com.diffplug.gradle.spotless.KotlinGradleExtension
import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins { id("com.diffplug.spotless") }

val buildInfo = extensions.create<BuildInfo>("buildInfo", project)

dependencyLocking { lockAllConfigurations() }

configurations {
  val rejectedVersionSuffix = Regex("-alpha|-beta|-eap|-m|-rc|-snapshot", RegexOption.IGNORE_CASE)
  configureEach {
    resolutionStrategy {
      componentSelection {
        all {
          if (rejectedVersionSuffix.containsMatchIn(candidate.version)) {
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

plugins.withType(JavaPlugin::class).configureEach {
  val java = project.extensions.getByType<JavaPluginExtension>()
  java.sourceCompatibility = JavaVersion.VERSION_17
  java.targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    jvmTarget = "17"
    freeCompilerArgs = freeCompilerArgs + listOf("-Xjsr305=strict", "-Xjvm-default=all")
  }
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
        url = uri("file:///$rootDir/build/m2")
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

// settings.gradle.kts sets `--write-locks`
// if Gradle command line contains this task name
val updateDependencyLocks by
  tasks.registering {
    doLast { configurations.filter { it.isCanBeResolved }.forEach { it.resolve() } }
  }

val allDependencies by tasks.registering(DependencyReportTask::class)

tasks.withType(Test::class).configureEach {
  System.getProperty("testReportsDir")?.let { reportsDir ->
    reports.junitXml.outputLocation.set(file(reportsDir).resolve(project.name).resolve(name))
  }
  debugOptions {
    enabled = System.getProperty("jvmdebug")?.toBoolean() ?: false
    @Suppress("UnstableApiUsage")
    host = "*"
    port = 5005
    suspend = true
    server = true
  }
}

tasks.withType(JavaExec::class).configureEach {
  debugOptions {
    enabled = System.getProperty("jvmdebug")?.toBoolean() ?: false
    @Suppress("UnstableApiUsage")
    host = "*"
    port = 5005
    suspend = true
    server = true
  }
}

// Version Catalog library symbols.
private val libs = the<LibrariesForLibs>()

private val licenseHeaderFile by lazy {
  rootProject.file("buildSrc/src/main/resources/license-header.star-block.txt")
}

private fun KotlinGradleExtension.configureFormatter() {
  ktfmt(libs.versions.ktfmt.get()).googleStyle()
  licenseHeaderFile(licenseHeaderFile, "([a-zA-Z]|@file|//)")
}

val originalRemoteName = System.getenv("PKL_ORIGINAL_REMOTE_NAME") ?: "origin"

spotless {
  ratchetFrom = "$originalRemoteName/main"

  // When building root project, format buildSrc files too.
  // We need this because buildSrc is not a subproject of the root project, so a top-level
  // `spotlessApply` will not trigger `buildSrc:spotlessApply`.
  if (project === rootProject) {
    kotlinGradle {
      configureFormatter()
      target("*.kts", "buildSrc/*.kts", "buildSrc/src/*/kotlin/**/*.kts")
    }
    kotlin {
      ktfmt(libs.versions.ktfmt.get()).googleStyle()
      target("buildSrc/src/*/kotlin/**/*.kt")
      licenseHeaderFile(licenseHeaderFile)
    }
  } else {
    kotlinGradle {
      configureFormatter()
      target("*.kts")
    }
  }
}
