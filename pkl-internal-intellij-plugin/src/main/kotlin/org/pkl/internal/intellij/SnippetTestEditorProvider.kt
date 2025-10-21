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
package org.pkl.internal.intellij

import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager

class SnippetTestEditorProvider : FileEditorProvider, DumbAware {

  private val hiddenExtensionRegex = Regex(".*[.]([^.]*)[.]pkl")

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return isSnippetTestInputFile(file) && findOutputFile(file) != null
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val textEditorProvider = TextEditorProvider.getInstance()
    val outputFile = findOutputFile(file) ?: return textEditorProvider.createEditor(project, file)

    val inputEditor = textEditorProvider.createEditor(project, file) as TextEditor
    val outputEditor = textEditorProvider.createEditor(project, outputFile) as TextEditor

    return SnippetTestSplitEditor(inputEditor, outputEditor)
  }

  override fun getEditorTypeId(): String = "snippet-test-split-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  private fun isSnippetTestInputFile(file: VirtualFile): Boolean {
    val path = file.path
    return path.contains("/src/test/files/") && path.contains("/input/") && file.extension == "pkl"
  }

  private fun possibleOutputPaths(testType: String, relativePath: String): String? {
    return when (testType) {
      "LanguageSnippetTests" ->
        if (relativePath.matches(hiddenExtensionRegex)) relativePath.dropLast(4)
        else relativePath.dropLast(3) + "pcf"
      "FormatterSnippetTests" -> relativePath
      "SnippetTests" -> relativePath.replaceAfterLast('.', "yaml")
      else -> null
    }
  }

  private fun findOutputFile(inputFile: VirtualFile): VirtualFile? {
    val path = inputFile.path
    val inputPattern = Regex(".*/src/test/files/(\\w+)/input/(.+)$")
    val match = inputPattern.find(path) ?: return null

    val testType = match.groupValues[1]
    val relativePath = match.groupValues[2]
    val relativeOutputPath = possibleOutputPaths(testType, relativePath) ?: return null
    val outputPath = path.replace("/input/$relativePath", "/output/$relativeOutputPath")
    val fileManager = VirtualFileManager.getInstance()
    return fileManager.findFileByUrl("file://$outputPath")
      ?: fileManager.findFileByUrl("file://${outputPath.replaceAfterLast('.', "err")}")
  }
}
