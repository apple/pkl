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
