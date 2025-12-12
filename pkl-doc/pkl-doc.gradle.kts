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
  pklJavaExecutable
  pklNativeExecutable
  pklHtmlValidator
  alias(libs.plugins.kotlinxSerialization)
}

executable {
  mainClass = "org.pkl.doc.Main"
  name = "pkldoc"
  javaName = "jpkldoc"
  documentationName = "Pkldoc CLI"
  publicationName = "pkldoc"
  javaPublicationName = "jpkldoc"
  website = "https://pkl-lang.org/main/current/pkl-doc/index.html"
}

dependencies {
  implementation(projects.pklCore)
  implementation(projects.pklCommonsCli)
  implementation(projects.pklCommons)
  implementation(projects.pklParser)
  implementation(libs.commonMark)
  implementation(libs.commonMarkTables)
  implementation(libs.kotlinxHtml)
  implementation(libs.kotlinxSerializationJson) {
    // use our own Kotlin version
    // (exclude is supported both for Maven and Gradle metadata, whereas dependency constraints
    // aren't)
    exclude(group = "org.jetbrains.kotlin")
  }
  implementation(libs.kotlinxCoroutinesCore) { exclude(group = "org.jetbrains.kotlin") }

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

val testNativeExecutable by
  tasks.registering(Test::class) {
    dependsOn(tasks.assembleNative)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    inputs.dir("src/test/files/DocGeneratorTest/input")
    outputs.dir("src/test/files/DocGeneratorTest/output")
    systemProperty("org.pkl.doc.NativeExecutableTest", "true")

    filter { includeTestsMatching("org.pkl.doc.NativeExecutableTest") }
  }

val testJavaExecutable by
  tasks.registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    dependsOn(tasks.javaExecutable)
    inputs.dir("src/test/files/DocGeneratorTest/input")
    outputs.dir("src/test/files/DocGeneratorTest/output")
    systemProperty("org.pkl.doc.JavaExecutableTest", "true")

    filter { includeTestsMatching("org.pkl.doc.JavaExecutableTest") }
  }

tasks.check { dependsOn(testJavaExecutable) }

tasks.testNative { dependsOn(testNativeExecutable) }

tasks.withType<NativeImageBuild> { extraNativeImageArgs.add("-H:IncludeResources=org/pkl/doc/.*") }

tasks.jar { manifest { attributes += mapOf("Main-Class" to "org.pkl.doc.Main") } }

htmlValidator { sources = files("src/test/files/DocGeneratorTest/output") }

tasks.validateHtml { mustRunAfter(testJavaExecutable) }
