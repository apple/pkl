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
  pklScalaLibrary
  pklFatJar
  pklPublishLibrary
  signing
}

dependencies {
  implementation(projects.pklConfigJava)
  api(libs.scalaReflect)
  firstPartySourcesJars(project(":pkl-core", "sourcesJar"))
}

tasks.shadowJar { archiveBaseName.set("pkl-config-scala-all") }

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-config-scala")
        description.set("Scala config library based on the Pkl config language.")
      }
    }

    named<MavenPublication>("fatJar") {
      artifactId = "pkl-config-scala-all"
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-config-scala")
        description.set(
          "Shaded fat Jar for pkl-config-scala, a Scala config library based on the Pkl config language."
        )
      }
    }
  }
}

signing { sign(publishing.publications["fatJar"]) }
