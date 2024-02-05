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
package org.pkl.commons.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import java.net.URI
import java.net.URISyntaxException
import org.pkl.commons.cli.CliException
import org.pkl.core.runtime.VmUtils
import org.pkl.core.util.IoUtils

abstract class BaseCommand(name: String, helpLink: String, help: String = "") :
    CliktCommand(
        name = name,
        help = help,
        epilog = "For more information, visit $helpLink",
    ) {
    val baseOptions by BaseOptions()

    /**
     * Parses [moduleName] into a URI. If scheme is not present, we expect that this is a file path
     * and encode any possibly invalid characters. If a scheme is present, we expect that this is a
     * valid URI.
     */
    protected fun parseModuleName(moduleName: String): URI =
        when (moduleName) {
            "-" -> VmUtils.REPL_TEXT_URI
            else ->
                try {
                    IoUtils.toUri(moduleName)
                } catch (e: URISyntaxException) {
                    val message = buildString {
                        append("Module URI `$moduleName` has invalid syntax (${e.reason}).")
                        if (e.index > -1) {
                            append("\n\n")
                            append(moduleName)
                            append("\n")
                            append(" ".repeat(e.index))
                            append("^")
                        }
                    }
                    throw CliException(message)
                }
        }
}
