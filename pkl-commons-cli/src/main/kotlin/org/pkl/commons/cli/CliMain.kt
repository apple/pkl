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
package org.pkl.commons.cli

import java.io.PrintStream
import kotlin.system.exitProcess
import org.fusesource.jansi.AnsiConsole

/** Building block for CLIs. Intended to be called from a `main` method. */
fun cliMain(block: () -> Unit) {
  fun printError(error: Throwable, stream: PrintStream) {
    val message = error.toString()
    stream.print(message)
    // ensure CLI output always ends with newline
    if (!message.endsWith('\n')) stream.println()
  }

  // Setup AnsiConsole. This will automatically strip escape codes if
  // the target shell doesn't appear to support them.
  AnsiConsole.systemInstall()
  // Force `native-image` to use system proxies (which does not happen with `-D`).
  System.setProperty("java.net.useSystemProxies", "true")
  try {
    block()
  } catch (e: CliTestException) {
    // no need to print the error, the test results will already do it
    exitProcess(e.exitCode)
  } catch (e: CliException) {
    printError(e, if (e.exitCode == 0) System.out else System.err)
    exitProcess(e.exitCode)
  } catch (e: Exception) {
    printError(CliBugException(e), System.err)
    exitProcess(1)
  }
}
