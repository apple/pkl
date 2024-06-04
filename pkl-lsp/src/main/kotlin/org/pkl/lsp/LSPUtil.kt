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
package org.pkl.lsp

import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern
import kotlin.math.max
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.pkl.lsp.ast.Span

private const val SIGNIFICAND_MASK = 0x000fffffffffffffL

private const val SIGNIFICAND_BITS = 52

private const val IMPLICIT_BIT: Long = SIGNIFICAND_MASK + 1

object LSPUtil {
  fun Span.toRange(): Range {
    val start = Position(beginLine - 1, beginCol - 1)
    val end = Position(endLine - 1, endCol - 1)
    return Range(start, end)
  }

  inline fun <reified T> List<*>.firstInstanceOf(): T? {
    return firstOrNull { it is T } as T?
  }
}

class UnexpectedTypeError(message: String) : AssertionError(message)

fun unexpectedType(obj: Any?): Nothing {
  throw UnexpectedTypeError(obj?.javaClass?.typeName ?: "null")
}

private fun takeLastSegment(name: String, separator: Char): String {
  val lastSep = name.lastIndexOf(separator)
  return name.substring(lastSep + 1)
}

private fun dropLastSegment(name: String, separator: Char): String {
  val lastSep = name.lastIndexOf(separator)
  return if (lastSep == -1) name else name.substring(0, lastSep)
}

private fun getNameWithoutExtension(path: String): String {
  val lastSep = max(path.lastIndexOf('/'), path.lastIndexOf('\\'))
  val lastDot = path.lastIndexOf('.')
  return if (lastDot == -1 || lastDot < lastSep) path.substring(lastSep + 1)
  else path.substring(lastSep + 1, lastDot)
}

fun inferImportPropertyName(moduleUriStr: String): String? {
  val moduleUri =
    try {
      URI(moduleUriStr)
    } catch (e: URISyntaxException) {
      return null
    }

  if (moduleUri.isOpaque) {
    // convention: take last segment of dot-separated name after stripping any colon-separated
    // version number
    return takeLastSegment(dropLastSegment(moduleUri.schemeSpecificPart, ':'), '.')
  }
  if (moduleUri.scheme == "package") {
    return moduleUri.fragment?.let(::getNameWithoutExtension)
  }
  if (moduleUri.isAbsolute) {
    return getNameWithoutExtension(moduleUri.path)
  }
  return getNameWithoutExtension(moduleUri.schemeSpecificPart)
}

fun isMathematicalInteger(x: Double): Boolean {
  val exponent = StrictMath.getExponent(x)
  return (exponent <= java.lang.Double.MAX_EXPONENT &&
    (x == 0.0 ||
      SIGNIFICAND_BITS - java.lang.Long.numberOfTrailingZeros(getSignificand(x)) <= exponent))
}

private fun getSignificand(d: Double): Long {
  val exponent = StrictMath.getExponent(d)
  assert(exponent <= java.lang.Double.MAX_EXPONENT)
  var bits = java.lang.Double.doubleToRawLongBits(d)
  bits = bits and SIGNIFICAND_MASK
  return if (exponent == java.lang.Double.MIN_EXPONENT - 1) bits shl 1 else bits or IMPLICIT_BIT
}

private val absoluteUriLike = Pattern.compile("\\w+:.*")

fun isAbsoluteUriLike(uriStr: String): Boolean = absoluteUriLike.matcher(uriStr).matches()

fun parseUriOrNull(uriStr: String): URI? =
  try {
    if (isAbsoluteUriLike(uriStr)) URI(uriStr) else URI(null, null, uriStr, null)
  } catch (_: URISyntaxException) {
    null
  }
