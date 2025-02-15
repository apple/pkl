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
plugins {
  pklAllProjects
  base
  `maven-publish`
  id("com.diffplug.spotless")
  pklPublishLibrary
  signing
}

// create and publish a self-contained stdlib archive
// purpose is to provide non-jvm tools/projects with a versioned stdlib
val stdlibZip by
  tasks.registering(Zip::class) {
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    archiveBaseName.set("pkl-stdlib")
    archiveVersion.set(project.version as String)
    into("org/pkl/stdlib") {
      from(projectDir)
      include("*.pkl")
    }
  }

tasks.assemble { dependsOn(stdlibZip) }

publishing {
  publications {
    create<MavenPublication>("stdlib") {
      artifactId = "pkl-stdlib"
      artifact(stdlibZip.flatMap { it.archiveFile })
      pom {
        description.set("Standard library for the Pkl programming language")
        url.set("https://github.com/apple/pkl/tree/main/stdlib")
      }
    }
  }
}

signing { sign(publishing.publications["stdlib"]) }

spotless {
  format("pkl") {
    target("*.pkl")
    licenseHeaderFile(
      rootProject.file("buildSrc/src/main/resources/license-header.line-comment.txt"),
      "/// ",
    )
  }
}
