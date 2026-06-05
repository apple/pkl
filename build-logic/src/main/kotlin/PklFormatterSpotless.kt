/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import com.diffplug.spotless.FormatterFunc
import com.diffplug.spotless.FormatterStep
import java.io.File
import java.io.Serial
import java.io.Serializable
import java.lang.reflect.Method
import java.net.URLClassLoader
import org.gradle.api.artifacts.Configuration

class PklFormatterStep(@Transient private val configuration: Configuration) : Serializable {
  companion object {
    @Serial private const val serialVersionUID: Long = 1L
  }

  fun create(): FormatterStep {
    val files = configuration.files.toList()
    return FormatterStep.createLazy(
      "pkl",
      { PklFormatterState(files) },
      { PklFormatterFunc(it.files) },
    )
  }
}

data class PklFormatterState(val files: List<File>) : Serializable {
  companion object {
    @Serial private const val serialVersionUID: Long = 1L
  }
}

class PklFormatterFunc(private val files: List<File>) : FormatterFunc, Serializable {
  companion object {
    @Serial private const val serialVersionUID: Long = 1L
  }

  @delegate:Transient
  private val classLoader: URLClassLoader by lazy {
    val urls = files.map { it.toURI().toURL() }
    // Use the platform classloader as parent to isolate from Gradle's classloader
    URLClassLoader(urls.toTypedArray(), ClassLoader.getPlatformClassLoader())
  }

  @delegate:Transient
  private val formatterClass: Class<*> by lazy {
    classLoader.loadClass("org.pkl.formatter.Formatter")
  }

  @delegate:Transient
  private val formatMethod: Method by lazy {
    formatterClass.getMethod("format", String::class.java)
  }

  @delegate:Transient
  private val formatterInstance: Any by lazy { formatterClass.getConstructor().newInstance() }

  override fun apply(input: String): String {
    return formatMethod(formatterInstance, input) as String
  }
}
