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

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JSplitPane

class SnippetTestSplitEditor(
  private val inputEditor: TextEditor,
  private val outputEditor: TextEditor,
) : UserDataHolderBase(), FileEditor {

  private var currentViewMode = ViewMode.SPLIT

  private val splitPane: JSplitPane =
    JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputEditor.component, outputEditor.component).apply {
      resizeWeight = 0.5
    }

  private val mainPanel =
    JBPanel<JBPanel<*>>(BorderLayout()).apply {
      add(createToolbar(), BorderLayout.NORTH)
      add(splitPane, BorderLayout.CENTER)
    }

  private fun createToolbar(): JComponent {
    val actionGroup =
      DefaultActionGroup().apply {
        add(ShowInputOnlyAction())
        add(ShowSplitAction())
      }

    val toolbar =
      ActionManager.getInstance().createActionToolbar("SnippetTestEditor", actionGroup, true)
    toolbar.targetComponent = mainPanel
    return toolbar.component
  }

  private fun setViewMode(mode: ViewMode) {
    if (currentViewMode == mode) return
    currentViewMode = mode

    // Remove the current center component
    val layout = mainPanel.layout as BorderLayout
    layout.getLayoutComponent(BorderLayout.CENTER)?.let { mainPanel.remove(it) }

    when (mode) {
      ViewMode.INPUT_ONLY -> {
        mainPanel.add(inputEditor.component, BorderLayout.CENTER)
      }
      ViewMode.SPLIT -> {
        // Re-add components to splitPane in case they were removed
        splitPane.leftComponent = inputEditor.component
        splitPane.rightComponent = outputEditor.component
        mainPanel.add(splitPane, BorderLayout.CENTER)
      }
    }

    mainPanel.revalidate()
    mainPanel.repaint()
  }

  override fun getComponent(): JComponent = mainPanel

  override fun getPreferredFocusedComponent(): JComponent? = inputEditor.preferredFocusedComponent

  override fun getName(): String = "Snippet Test"

  override fun getFile(): VirtualFile? = inputEditor.file

  override fun setState(state: FileEditorState) {
    if (state is SnippetTestSplitEditorState) {
      inputEditor.setState(state.inputState)
      outputEditor.setState(state.outputState)
    }
  }

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    return SnippetTestSplitEditorState(inputEditor.getState(level), outputEditor.getState(level))
  }

  override fun isModified(): Boolean = inputEditor.isModified || outputEditor.isModified

  override fun isValid(): Boolean = inputEditor.isValid && outputEditor.isValid

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    inputEditor.addPropertyChangeListener(listener)
    outputEditor.addPropertyChangeListener(listener)
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    inputEditor.removePropertyChangeListener(listener)
    outputEditor.removePropertyChangeListener(listener)
  }

  override fun getCurrentLocation(): FileEditorLocation? = inputEditor.currentLocation

  override fun dispose() {
    inputEditor.dispose()
    outputEditor.dispose()
  }

  private enum class ViewMode {
    INPUT_ONLY,
    SPLIT,
  }

  private inner class ShowInputOnlyAction :
    ToggleAction("Show Input Only", "Show only the input file", AllIcons.General.LayoutEditorOnly) {
    override fun isSelected(e: AnActionEvent): Boolean = currentViewMode == ViewMode.INPUT_ONLY

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) setViewMode(ViewMode.INPUT_ONLY)
    }
  }

  private inner class ShowSplitAction :
    ToggleAction(
      "Show Split",
      "Show input and output side by side",
      AllIcons.General.LayoutEditorPreview,
    ) {
    override fun isSelected(e: AnActionEvent): Boolean = currentViewMode == ViewMode.SPLIT

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) setViewMode(ViewMode.SPLIT)
    }
  }
}

data class SnippetTestSplitEditorState(
  val inputState: FileEditorState,
  val outputState: FileEditorState,
) : FileEditorState {
  override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean {
    return otherState is SnippetTestSplitEditorState &&
      inputState.canBeMergedWith(otherState.inputState, level) &&
      outputState.canBeMergedWith(otherState.outputState, level)
  }
}
