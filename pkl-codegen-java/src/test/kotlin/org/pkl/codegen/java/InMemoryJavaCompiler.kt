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
package org.pkl.codegen.java

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import javax.tools.*

class CompilationFailedException(msg: String) : RuntimeException(msg)

object InMemoryJavaCompiler {
  fun compile(sourceFiles: Map<String, String>): Map<String, Class<*>> {
    val compiler = ToolProvider.getSystemJavaCompiler()
    val diagnosticsCollector = DiagnosticCollector<JavaFileObject>()
    val fileManager =
      InMemoryFileManager(compiler.getStandardFileManager(diagnosticsCollector, null, null))
    val sourceObjects =
      sourceFiles
        .filter { (filename, _) -> filename.endsWith(".java") }
        .map { (filename, contents) -> ReadableSourceFileObject(filename, contents) }
    val task = compiler.getTask(null, fileManager, diagnosticsCollector, null, null, sourceObjects)
    val result = task.call()
    if (!result) {
      throw CompilationFailedException(
        buildString {
          appendLine("Compilation failed. Error(s):")
          for (diagnostic in diagnosticsCollector.diagnostics) {
            appendLine(diagnostic.getMessage(null))
          }
        }
      )
    }
    val loader = ClassFileObjectLoader(fileManager.outputFiles)
    return fileManager.outputFiles.mapValues { loader.loadClass(it.key) }
  }
}

private class ClassFileObjectLoader(val fileObjects: Map<String, WritableBinaryFileObject>) :
  ClassLoader(ClassFileObjectLoader::class.java.classLoader) {

  override fun findClass(name: String): Class<*> {
    val obj = fileObjects[name]
    if (obj == null || obj.kind != JavaFileObject.Kind.CLASS) {
      throw ClassNotFoundException(name)
    }
    val array = obj.out.toByteArray()
    return defineClass(name, array, 0, array.size)
  }
}

private class ReadableSourceFileObject(path: String, private val contents: String) :
  SimpleJavaFileObject(URI(path), JavaFileObject.Kind.SOURCE) {

  override fun openInputStream(): InputStream = contents.byteInputStream()

  override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = contents
}

private class WritableBinaryFileObject(className: String, kind: JavaFileObject.Kind) :
  SimpleJavaFileObject(URI("/${className.replace(".", "/")}.${kind.extension}"), kind) {
  val out = ByteArrayOutputStream()

  override fun openOutputStream(): OutputStream = out
}

private class InMemoryFileManager(delegate: JavaFileManager) :
  ForwardingJavaFileManager<JavaFileManager>(delegate) {

  val outputFiles = mutableMapOf<String, WritableBinaryFileObject>()

  override fun getJavaFileForOutput(
    location: JavaFileManager.Location,
    className: String,
    kind: JavaFileObject.Kind,
    sibling: FileObject,
  ): JavaFileObject {

    return WritableBinaryFileObject(className, kind).also { outputFiles[className] = it }
  }
}
