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

import java.io.*
import java.nio.charset.Charset
import java.nio.file.*
import java.nio.file.attribute.FileAttribute
import java.util.stream.Stream
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isSymbolicLink

// not stored to avoid build-time initialization by native-image
val currentWorkingDir: Path
  get() = System.getProperty("user.dir").toPath()

// unlike `Path.resolve`, this works across file systems if `other` is absolute
fun Path.resolveSafely(other: Path): Path = if (other.isAbsolute) other else resolve(other)

@Throws(IOException::class)
fun Path.walk(maxDepth: Int = Int.MAX_VALUE, vararg options: FileVisitOption): Stream<Path> =
  Files.walk(this, maxDepth, *options)

@Throws(IOException::class)
fun Path.createTempFile(
  prefix: String? = null,
  suffix: String? = null,
  vararg attributes: FileAttribute<*>
): Path = Files.createTempFile(this, prefix, suffix, *attributes)

@Throws(IOException::class)
fun Path.createParentDirectories(vararg attributes: FileAttribute<*>): Path = apply {
  // Files.createDirectories will throw a FileAlreadyExistsException
  // if the file exists and is not a directory and symlinks are never
  // directories
  if (parent?.isSymbolicLink() != true) {
    parent?.createDirectories(*attributes)
  }
}

/** [Files.writeString] seems more efficient than [kotlin.io.path.writeText]. */
@Throws(IOException::class)
fun Path.writeString(
  text: String,
  charset: Charset = Charsets.UTF_8,
  vararg options: OpenOption
): Path = Files.writeString(this, text, charset, *options)

/** [Files.readString] seems more efficient than [kotlin.io.path.readText]. */
@Throws(IOException::class)
fun Path.readString(charset: Charset = Charsets.UTF_8): String = Files.readString(this, charset)

@Throws(IOException::class)
fun Path.deleteRecursively() {
  if (exists()) {
    walk().use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { it.deleteIfExists() } }
  }
}

@Throws(IOException::class)
fun Path.copyRecursively(target: Path) {
  if (exists()) {
    target.createParentDirectories()
    walk().use { paths ->
      paths.forEach { src ->
        val dst = target.resolve(this@copyRecursively.relativize(src))
        src.copyTo(dst, overwrite = true)
      }
    }
  }
}

private val isWindows by lazy { System.getProperty("os.name").contains("Windows") }

/** Copy implementation from IoUtils.toNormalizedPathString */
fun Path.toNormalizedPathString(): String {
  if (isWindows) {
    return toString().replace("\\", "/")
  }
  return toString()
}
