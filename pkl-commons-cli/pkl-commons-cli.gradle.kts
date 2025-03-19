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
  pklKotlinLibrary
  pklPublishLibrary
}

val svmClasspath: Configuration by configurations.creating

// used by pklNativeExecutable.gradle.kts
@Suppress("unused") val svm: SourceSet by sourceSets.creating { compileClasspath = svmClasspath }

dependencies {
  api(projects.pklCore)
  api(libs.clikt)
  implementation(libs.cliktMarkdown)

  implementation(projects.pklCommons)
  testImplementation(projects.pklCommonsTest)

  svmClasspath(libs.svm)
  svmClasspath(libs.truffleSvm)
  svmClasspath(projects.pklCore)
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-commons-cli")
        description.set("Internal CLI utilities. NOT A PUBLIC API.")
      }
    }
  }
}
