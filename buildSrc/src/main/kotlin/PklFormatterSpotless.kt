/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import java.io.Serial
import java.io.Serializable
import java.net.URLClassLoader
import org.gradle.api.artifacts.Configuration

class PklFormatterStep(@Transient private val configuration: Configuration) : Serializable {
  companion object {
    @Serial private const val serialVersionUID: Long = 1L
  }

  fun create(): FormatterStep {
    return FormatterStep.createLazy(
      "pkl",
      { PklFormatterStep(configuration) },
      { PklFormatterFunc(configuration) },
    )
  }
}

class PklFormatterFunc(@Transient private val configuration: Configuration) :
  FormatterFunc, Serializable {
  companion object {
    @Serial private const val serialVersionUID: Long = 1L
  }

  private val formatterClass by lazy {
    val urls = configuration.files.map { it.toURI().toURL() }
    val classLoader = URLClassLoader(urls.toTypedArray())
    classLoader.loadClass("org.pkl.formatter.Formatter")
  }

  private val formatMethod by lazy { formatterClass.getMethod("format", String::class.java) }

  private val formatterInstance by lazy { formatterClass.getConstructor().newInstance() }

  override fun apply(input: String): String {
    return formatMethod(formatterInstance, input) as String
  }
}
