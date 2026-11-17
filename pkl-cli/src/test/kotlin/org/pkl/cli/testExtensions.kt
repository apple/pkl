/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.cli

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import org.pkl.commons.writeString

fun Path.writeFile(fileName: String, contents: String): Path {
  return resolve(fileName).apply {
    createParentDirectories()
    writeString(contents, StandardCharsets.UTF_8)
  }
}

fun Path.writeEmptyFile(fileName: String): Path = writeFile(fileName, "")

/**
 * The command that launches Pkl in subprocess tests: the native executable if
 * `org.pkl.cli.testExecutable` is set, otherwise the executable jar given by `org.pkl.cli.testJar`.
 */
fun subprocessLauncher(): Array<String> {
  System.getProperty("org.pkl.cli.testExecutable")?.let {
    return arrayOf(it)
  }
  val jar =
    checkNotNull(System.getProperty("org.pkl.cli.testJar")) {
      "neither system property `org.pkl.cli.testExecutable` nor `org.pkl.cli.testJar` is set"
    }
  val javaBin = ProcessHandle.current().info().command().orElseThrow()
  return arrayOf(javaBin, "-jar", jar)
}
