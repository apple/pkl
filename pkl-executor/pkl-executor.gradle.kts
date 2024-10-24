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
import java.nio.file.Files
import java.nio.file.LinkOption

plugins {
  pklAllProjects
  pklJavaLibrary
  pklPublishLibrary
  pklKotlinTest
}

val pklDistributionCurrent: Configuration by configurations.creating
val pklHistoricalDistributions: Configuration by configurations.creating

// Because pkl-executor doesn't depend on other Pkl modules
// (nor has overlapping dependencies that could cause a version conflict),
// clients are free to use different versions of pkl-executor and (say) pkl-config-java-all.
// (Pkl distributions used by EmbeddedExecutor are isolated via class loaders.)
dependencies {
  pklDistributionCurrent(project(":pkl-config-java", "fatJar"))
  @Suppress("UnstableApiUsage") pklHistoricalDistributions(libs.pklConfigJavaAll025)

  implementation(libs.slf4jApi)

  testImplementation(projects.pklCommonsTest)
  testImplementation(projects.pklCore)
  testImplementation(libs.slf4jSimple)
}

// TODO why is this needed? Without this, we get error:
// `Entry org/pkl/executor/EmbeddedExecutor.java is a duplicate but no duplicate handling strategy
// has been set.`
// However, we do not have multiple of these Java files.
tasks.named<Jar>("sourcesJar") { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-executor")
        description.set(
          """
          Library for executing Pkl code in a sandboxed environment.
        """
            .trimIndent()
        )
      }
    }
  }
}

sourceSets { main { java { srcDir("src/main/java") } } }

val prepareHistoricalDistributions by
  tasks.registering {
    val outputDir = layout.buildDirectory.dir("pklHistoricalDistributions")
    inputs.files(pklHistoricalDistributions.files())
    outputs.dir(outputDir)
    doLast {
      val distributionDir = outputDir.get().asFile.toPath().also(Files::createDirectories)
      for (file in pklHistoricalDistributions.files) {
        val target = distributionDir.resolve(file.name)
        // Create normal files on Windows, symlink on macOS/linux (need admin priveleges to create
        // symlinks on Windows)
        if (buildInfo.os.isWindows) {
          if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.exists(target)) {
              Files.delete(target)
            }
            Files.copy(file.toPath(), target)
          }
        } else {
          if (!Files.isSymbolicLink(target)) {
            if (Files.exists(target)) {
              Files.delete(target)
            }
            Files.createSymbolicLink(target, file.toPath())
          }
        }
      }
    }
  }

val prepareTest by
  tasks.registering { dependsOn(pklDistributionCurrent, prepareHistoricalDistributions) }

tasks.test { dependsOn(prepareTest) }
