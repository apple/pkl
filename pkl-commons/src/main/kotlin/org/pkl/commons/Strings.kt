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
package org.pkl.commons

import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.*
import java.util.regex.Pattern

fun String.toPath(): Path = Path.of(this)

private val uriLike = Pattern.compile("\\w+:[^\\\\].*")

private val windowsPathLike = Pattern.compile("\\w:\\\\.*")

/** Copy of org.pkl.core.util.IoUtils.toUri */
fun String.toUri(): URI {
  if (uriLike.matcher(this).matches()) {
    return URI(this)
  }
  if (windowsPathLike.matcher(this).matches()) {
    return File(this).toURI()
  }
  return URI(null, null, this, null)
}

/** Lex a string into tokens similar to how a shell would */
fun shlex(input: String): List<String> {
  val result = mutableListOf<String>()
  var inEscape = false
  var quote: Char? = null
  var lastCloseQuoteIndex = Int.MIN_VALUE
  val current = StringBuilder()

  for ((idx, char) in input.withIndex()) {
    when {
      // if in an escape always append the next character
      inEscape -> {
        inEscape = false
        current.append(char)
      }
      // enter an escape on \ if not in a quote or in a non-single quote
      char == '\\' && quote != '\'' -> inEscape = true
      // if in a quote and encounter the delimiter, tentatively exit the quote
      // this handles cases with adjoining quotes e.g. `abc'123''xyz'`
      quote == char -> {
        quote = null
        lastCloseQuoteIndex = idx
      }
      // if not in a quote and encounter a quote character, enter a quote
      quote == null && (char == '\'' || char == '"') -> {
        quote = char
      }
      // if not in a quote and whitespace is encountered
      quote == null && char.isWhitespace() -> {
        // if the current token isn't empty or if a quote has just ended, finalize the current token
        // otherwise do nothing, which handles multiple whitespace cases e.g. `abc     123`
        if (current.isNotEmpty() || lastCloseQuoteIndex == (idx - 1)) {
          result.add(current.toString())
          current.clear()
        }
      }
      // in other cases, append to the current token
      else -> current.append(char)
    }
  }
  // clean up last token
  // if the current token isn't empty or if a quote has just ended, finalize the token
  // if this condition is false, the input likely ended in whitespace
  if (current.isNotEmpty() || lastCloseQuoteIndex == (input.length - 1)) {
    result.add(current.toString())
  }

  return result
}
