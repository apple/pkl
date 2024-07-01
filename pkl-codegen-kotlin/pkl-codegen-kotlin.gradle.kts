/**
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
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-codegen-kotlin")
        description.set(
          """
          Kotlin source code generator that generates corresponding Kotlin classes for Pkl classes,
          simplifying consumption of Pkl configuration as statically typed Kotlin objects.
        """
            .trimIndent()
        )
      }
    }
  }
}

tasks.jar { manifest { attributes += mapOf("Main-Class" to "org.pkl.codegen.kotlin.Main") } }

dependencies {
  implementation(projects.pklCommons)
  api(projects.pklCommonsCli)
  api(projects.pklCore)

  implementation(libs.kotlinPoet)
  implementation(libs.kotlinReflect)

  testImplementation(projects.pklConfigKotlin)
  testImplementation(projects.pklCommonsTest)
  testImplementation(libs.kotlinCompilerEmbeddable)
  testRuntimeOnly(libs.kotlinScriptingCompilerEmbeddable)
  testRuntimeOnly(libs.kotlinScriptUtil)
}
