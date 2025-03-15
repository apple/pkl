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
  pklJavaLibrary
  pklKotlinLibrary
  pklNativeBuild
}

dependencies {
  implementation(projects.pklCore)
  implementation(libs.msgpack)
  implementation(libs.truffleApi)

  testImplementation(projects.pklCommonsTest)
}

tasks.test {
  inputs
    .dir("src/test/files/SnippetTests/input")
    .withPropertyName("snippetTestsInput")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  inputs
    .dir("src/test/files/SnippetTests/output")
    .withPropertyName("snippetTestsOutput")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  exclude("**/NativeServerTest.*")
}

private fun Test.configureNativeTest() {
  testClassesDirs = files(tasks.test.get().testClassesDirs)
  classpath = tasks.test.get().classpath
  include("**/NativeServerTest.*")
}

val testMacExecutableAarch64 by
  tasks.registering(Test::class) {
    dependsOn(":pkl-cli:macExecutableAarch64")
    configureNativeTest()
  }

val testMacExecutableAmd64 by
  tasks.registering(Test::class) {
    dependsOn(":pkl-cli:macExecutableAmd64")
    configureNativeTest()
  }

val testLinuxExecutableAmd64 by
  tasks.registering(Test::class) {
    dependsOn(":pkl-cli:linuxExecutableAmd64")
    configureNativeTest()
  }

val testLinuxExecutableAarch64 by
  tasks.registering(Test::class) {
    dependsOn(":pkl-cli:linuxExecutableAarch64")
    configureNativeTest()
  }

val testAlpineExecutableAmd64 by
  tasks.registering(Test::class) {
    dependsOn(":pkl-cli:alpineExecutableAmd64")
    configureNativeTest()
  }

val testWindowsExecutableAmd64 by
  tasks.registering(Test::class) {
    dependsOn(":pkl-cli:windowsExecutableAmd64")
    configureNativeTest()
  }

tasks.testNativeMacOsAarch64 { dependsOn(testMacExecutableAarch64) }

tasks.testNativeMacOsAmd64 { dependsOn(testMacExecutableAmd64) }

tasks.testNativeLinuxAarch64 { dependsOn(testLinuxExecutableAarch64) }

tasks.testNativeLinuxAmd64 { dependsOn(testLinuxExecutableAmd64) }

tasks.testNativeAlpineLinuxAmd64 { dependsOn(testAlpineExecutableAmd64) }

tasks.testNativeWindowsAmd64 { dependsOn(testWindowsExecutableAmd64) }
