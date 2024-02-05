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
package org.pkl.cli.repl

import org.pkl.core.Release

internal object ReplMessages {
    val welcome =
        """
    Welcome to Pkl ${Release.current().version()}.
    Type an expression to have it evaluated.
    Type `:help` or `:examples` for more information.
  """
            .trimIndent()

    val help =
        """
    `<expr>`           Evaluate <expr> and print the result. `1 + 3`
    `<name> = <expr>`  Evaluate <expr> and assign the result to property <name>. `msg = "howdy"`
    `:clear`           Clear the screen.
    `:examples`        Show code examples (use copy and paste to run them).
    `:force <expr>`    Force eager evaluation of a value.
    `:help`            Show this help.
    `:load <file>`     Load <file> from local file system. `:load path/to/config.pkl`
    `:quit`            Quit this program.
    `:reset`           Reset the environment to its initial state.

    Tips:
    * Commands can be abbreviated. `:h`
    * Commands can be completed. `:<TAB>`
    * File paths can be completed. `:load <TAB>`
    * Expressions can be completed. `"hello".re<TAB>`
    * Multiple declarations and expressions can be evaluated at once. `a = 1; b = a + 2`
    * Incomplete input will be continued on the next line.
    * Multi-line programs can be copy-pasted into the REPL.

  """
            .trimIndent()

    val examples: String =
        """
    Expressions:
    `2 + 3 * 4`

    Strings:
    `"Hello, " + "World!"`

    Properties:
    `timeout = 5.min; timeout`

    Objects:
    ```pigeon {
      name = "Pigeon"
      fullName = "\(name) Bird"
      age = 42
      address {
        street = "Landers St."
      }
    }
    pigeon.fullName
    
    hobbies {
      "Swimming"
      "Dancing"
      "Surfing"
    }
    hobbies[1]
    
    prices {
      ["Apple"] = 1.5
      ["Orange"] = 5
      ["Banana"] = 2
    }
    prices["Banana"]```
    
    Inheritance:
    ```parrot = (pigeon) {
      name = "Parrot"
      age = 41
    }
    :force parrot```

    For more examples, see the Language Reference${if (isMacOs()) " (Command+Double-click the link below)" else ""}:

    ${Release.current().documentation().homepage()}language-reference/

  """
            .trimIndent()

    private fun isMacOs() = System.getProperty("os.name").equals("Mac OS X", ignoreCase = true)
}
