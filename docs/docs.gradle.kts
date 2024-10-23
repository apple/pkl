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
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
  pklAllProjects
  pklKotlinTest
}

sourceSets {
  test {
    java {
      srcDir(file("modules/pkl-core/examples"))
      srcDir(file("modules/pkl-config-java/examples"))
      srcDir(file("modules/java-binding/examples"))
    }
    val kotlin = project.extensions.getByType<KotlinJvmProjectExtension>().sourceSets[name].kotlin
    kotlin.srcDir(file("modules/kotlin-binding/examples"))
  }
}

dependencies {
  testImplementation(projects.pklCore)
  testImplementation(projects.pklConfigJava)
  testImplementation(projects.pklConfigKotlin)
  testImplementation(projects.pklCommonsTest)
  testImplementation(libs.junitEngine)
  testImplementation(libs.antlrRuntime)
}

tasks.test {
  inputs
    .files(fileTree("modules").matching { include("**/pages/*.adoc") })
    .withPropertyName("asciiDocFiles")
    .withPathSensitivity(PathSensitivity.RELATIVE)
}
