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
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Builds a self-contained Pkl CLI Jar that is directly executable on *nix and executable with `java
 * -jar` on Windows.
 *
 * For direct execution, the `java` command must be on the PATH.
 *
 * https://skife.org/java/unix/2011/06/20/really_executable_jars.html
 */
abstract class ExecutableJar : DefaultTask() {
  @get:InputFile abstract val inJar: RegularFileProperty

  @get:OutputFile abstract val outJar: RegularFileProperty

  @get:Input abstract val jvmArgs: ListProperty<String>

  @TaskAction
  fun buildJar() {
    val inFile = inJar.get().asFile
    val outFile = outJar.get().asFile
    val escapedJvmArgs = jvmArgs.get().joinToString(separator = " ") { "\"$it\"" }
    val startScript =
      """
      #!/bin/sh
      exec java $escapedJvmArgs -jar $0 "$@"
    """
        .trimIndent() + "\n\n\n"
    outFile.outputStream().use { outStream ->
      startScript.byteInputStream().use { it.copyTo(outStream) }
      inFile.inputStream().use { it.copyTo(outStream) }
    }

    // chmod a+x
    outFile.setExecutable(true, false)
  }
}
