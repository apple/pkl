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
package org.pkl.doc

import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createParentDirectories
import org.pkl.core.*
import org.pkl.core.util.IoUtils
import org.pkl.parser.Lexer

// overwrites any existing file
internal fun copyResource(resourceName: String, targetDir: Path) {
  val targetFile = targetDir.resolve(resourceName).apply { createParentDirectories() }
  Files.copy(getResourceAsStream(resourceName), targetFile, StandardCopyOption.REPLACE_EXISTING)
}

internal fun getResourceAsStreamOrNull(resourceName: String): InputStream? =
  Thread.currentThread().contextClassLoader.getResourceAsStream("org/pkl/doc/$resourceName")

internal fun getResourceAsStream(resourceName: String): InputStream =
  getResourceAsStreamOrNull(resourceName)
    ?: throw DocGeneratorException("Failed to load class path resource `$resourceName`.")

internal val ModuleSchema?.hasListedClass: Boolean
  get() = this != null && allClasses.any { !it.value.isUnlisted }

internal val ModuleSchema?.hasListedTypeAlias: Boolean
  get() = this != null && allTypeAliases.any { !it.value.isUnlisted }

internal val PClass?.hasListedProperty: Boolean
  get() = this != null && allProperties.any { !it.value.isUnlisted }

internal val PClass?.hasListedMethod: Boolean
  get() = this != null && allMethods.any { !it.value.isUnlisted }

internal val Member.isUnlisted: Boolean
  get() = annotations.isUnlisted

internal val List<PObject>.isUnlisted: Boolean
  get() = any { it.classInfo == PClassInfo.Unlisted }

internal val List<PObject>.deprecation: String?
  get() = find { it.classInfo == PClassInfo.Deprecated }?.get("message") as String?

@Suppress("UNCHECKED_CAST", "unused")
internal val List<PObject>.alsoKnownAs: List<String>?
  get() = find { it.classInfo == PClassInfo.AlsoKnownAs }?.get("names") as List<String>?

internal fun createDeprecatedAnnotation(message: String): PObject =
  PObject(PClassInfo.Deprecated, mapOf("message" to message, "replaceWith" to PNull.getInstance()))

private val paragraphSeparatorRegex: Regex = Regex("(?m:^\\s*(`{3,}\\w*)?\\s*\n)")

internal fun getDocCommentSummary(docComment: String?): String? {
  if (docComment == null) return null

  return when (val match = paragraphSeparatorRegex.find(docComment)) {
    null -> docComment.trim().ifEmpty { null }
    else -> docComment.substring(0, match.range.first).trim().ifEmpty { null }
  }
}

internal fun getDocCommentOverflow(docComment: String?): String? {
  if (docComment == null) return null

  return when (val match = paragraphSeparatorRegex.find(docComment)) {
    null -> null
    else -> {
      val index = if (match.groups[1] != null) match.range.first else match.range.last + 1
      docComment.substring(index).trim().ifEmpty { null }
    }
  }
}

internal fun String.replaceSourceCodePlaceholders(
  path: String,
  sourceLocation: Member.SourceLocation,
): String {
  return replace("%{path}", path)
    .replace("%{line}", sourceLocation.startLine().toString())
    .replace("%{endLine}", sourceLocation.endLine().toString())
}

/** Encodes a URI string, encoding characters that are part of URI syntax. */
internal val String.uriEncodedComponent
  get(): String {
    val ret = URI(null, null, this, null)
    return ret.toString().replace("/", "%2F")
  }

/**
 * Encodes a URI string, preserving characters that are part of URI syntax.
 *
 * Follows `encodeURI` from ECMAScript.
 */
internal val String.uriEncoded
  get(): String = replace(Regex("([^;/?:@&=+\$,#]+)")) { it.value.uriEncodedComponent }

fun getModulePath(moduleName: String, packagePrefix: String): String =
  moduleName.substring(packagePrefix.length).replace('.', '/')

/**
 * Turns `"foo.bar.baz-biz"` into ``"foo.bar.`baz-biz`"``.
 *
 * There's a chance that this is wrong; a module might look like: ``"module foo.`bar.baz`.biz"``.
 * However, we don't keep around enough information to render this faithfully.
 */
internal val String.asModuleName: String
  get() = split(".").map(Lexer::maybeQuoteIdentifier).joinToString(".") { it }

internal val String.asIdentifier: String
  get() = Lexer.maybeQuoteIdentifier(this)

internal val String.pathEncoded
  get(): String = IoUtils.encodePath(this)

fun OutputStream.write(str: String) = write(str.toByteArray(Charsets.UTF_8))

fun OutputStream.writeLine(str: String) = write((str + "\n").toByteArray(Charsets.UTF_8))

operator fun <A, B> org.pkl.core.util.Pair<A, B>.component1(): A = first

operator fun <A, B> org.pkl.core.util.Pair<A, B>.component2(): B = second
