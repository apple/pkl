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

class SnippetTestEditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = file.isSnippetTestInputFile()

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val textEditorProvider = TextEditorProvider.getInstance()
    val outputFile = findOutputFile(file)

    val inputEditor = textEditorProvider.createEditor(project, file) as TextEditor
    val outputEditor =
      outputFile?.let { textEditorProvider.createEditor(project, it) as TextEditor }

    return SnippetTestSplitEditor(inputEditor, outputEditor)
  }

  override fun getEditorTypeId(): String = "snippet-test-split-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
