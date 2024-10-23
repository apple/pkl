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
plugins {
  pklAllProjects
  pklKotlinLibrary
  pklPublishLibrary
  pklHtmlValidator
  @Suppress("DSL_SCOPE_VIOLATION") // https://youtrack.jetbrains.com/issue/KTIJ-19369
  alias(libs.plugins.kotlinxSerialization)
}

dependencies {
  implementation(projects.pklCore)
  implementation(projects.pklCommonsCli)
  implementation(projects.pklCommons)
  implementation(libs.commonMark)
  implementation(libs.commonMarkTables)
  implementation(libs.kotlinxHtml)
  implementation(libs.kotlinxSerializationJson) {
    // use our own Kotlin version
    // (exclude is supported both for Maven and Gradle metadata, whereas dependency constraints
    // aren't)
    exclude(group = "org.jetbrains.kotlin")
  }

  testImplementation(projects.pklCommonsTest)
  testImplementation(libs.jimfs)

  // Graal.JS
  testImplementation(libs.graalSdk)
  testImplementation(libs.graalJs)
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-doc")
        description.set("Documentation generator for Pkl modules.")
      }
    }
  }
}

tasks.jar { manifest { attributes += mapOf("Main-Class" to "org.pkl.doc.Main") } }

htmlValidator { sources = files("src/test/files/DocGeneratorTest/output") }
