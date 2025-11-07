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

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.Executor
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.junit.JUnitConfigurationType
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JSplitPane

class SnippetTestSplitEditor(
  private val inputEditor: TextEditor,
  private var outputEditor: TextEditor?,
) : UserDataHolderBase(), FileEditor {

  private var currentViewMode = if (outputEditor != null) ViewMode.SPLIT else ViewMode.INPUT_ONLY

  private val splitPane: JSplitPane =
    JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputEditor.component, outputEditor?.component).apply {
      resizeWeight = 0.5
    }

  private val mainPanel =
    JBPanel<JBPanel<*>>(BorderLayout()).apply {
      add(createToolbar(), BorderLayout.NORTH)
      when (currentViewMode) {
        ViewMode.INPUT_ONLY -> add(inputEditor.component, BorderLayout.CENTER)
        ViewMode.SPLIT -> add(splitPane, BorderLayout.CENTER)
      }
    }

  private fun createToolbar(): JComponent {
    val actionGroup =
      DefaultActionGroup().apply {
        add(RunTestAction())
        add(DebugTestAction())
        add(RunAllTestsAction())
        add(OverwriteSnippetAction())
        addSeparator()
        add(ShowInputOnlyAction())
        add(ShowSplitAction())
      }

    val toolbar =
      ActionManager.getInstance().createActionToolbar("SnippetTestEditor", actionGroup, true)
    toolbar.targetComponent = mainPanel
    return toolbar.component
  }

  private fun setViewMode(mode: ViewMode) {
    if (currentViewMode == mode || outputEditor == null) return
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
        splitPane.rightComponent = outputEditor!!.component
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
      if (outputEditor != null && state.outputState != null) {
        outputEditor!!.setState(state.outputState)
      }
    }
  }

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    return SnippetTestSplitEditorState(inputEditor.getState(level), outputEditor?.getState(level))
  }

  override fun isModified(): Boolean = inputEditor.isModified || outputEditor?.isModified ?: false

  override fun isValid(): Boolean = inputEditor.isValid && outputEditor?.isValid ?: true

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    inputEditor.addPropertyChangeListener(listener)
    outputEditor?.addPropertyChangeListener(listener)
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    inputEditor.removePropertyChangeListener(listener)
    outputEditor?.removePropertyChangeListener(listener)
  }

  override fun getCurrentLocation(): FileEditorLocation? = inputEditor.currentLocation

  override fun dispose() {
    inputEditor.dispose()
    outputEditor?.dispose()
  }

  /**
   * Refreshes the output editor by reloading the output file from disk. This is useful after
   * running tests, as the output file may have been created or changed (e.g., from .pcf to .err).
   */
  private fun refreshOutputEditor(project: Project) {
    val inputFile = inputEditor.file ?: return

    // Refresh the input file's parent to pick up any new output files
    ApplicationManager.getApplication().invokeLater {
      VirtualFileManager.getInstance().asyncRefresh {
        val currentOutputFile = findOutputFile(inputFile)
        val editorOutputFile = outputEditor?.file
        when {
          currentOutputFile != null && currentOutputFile != editorOutputFile -> {
            // No output file exists; set split mode.
            if (editorOutputFile == null) {
              replaceOutputEditorAndSetSplitMode(project, currentOutputFile)
            } else {
              // The output file has changed (e.g., .pcf to .err or vice versa), or got created.
              // We need to replace the output editor with a new one for the new file
              replaceOutputEditor(project, currentOutputFile)
            }
          }
          else -> currentOutputFile?.refresh(true, false)
        }
      }
    }
  }

  /**
   * Replaces the current output editor with a new one for the specified file. This is necessary
   * when the output file type changes (e.g., from .pcf to .err).
   */
  private fun replaceOutputEditorAndSetSplitMode(project: Project, newOutputFile: VirtualFile) {
    ApplicationManager.getApplication().invokeLater {
      val textEditorProvider = TextEditorProvider.getInstance()
      val newEditor = textEditorProvider.createEditor(project, newOutputFile) as TextEditor

      // Dispose the old editor
      outputEditor?.dispose()

      // Update the reference to the new editor
      outputEditor = newEditor
      setViewMode(ViewMode.SPLIT)
    }
  }

  /**
   * Replaces the current output editor with a new one for the specified file. This is necessary
   * when the output file type changes (e.g., from .pcf to .err).
   */
  private fun replaceOutputEditor(project: Project, newOutputFile: VirtualFile) {
    ApplicationManager.getApplication().invokeLater {
      val textEditorProvider = TextEditorProvider.getInstance()
      val newEditor = textEditorProvider.createEditor(project, newOutputFile) as TextEditor

      // Dispose the old editor
      outputEditor?.dispose()

      // Update the reference to the new editor
      outputEditor = newEditor

      // Update the split pane with the new editor
      when (currentViewMode) {
        ViewMode.SPLIT -> {
          splitPane.rightComponent = newEditor.component
          splitPane.revalidate()
          splitPane.repaint()
        }
        ViewMode.INPUT_ONLY -> {
          // Nothing to do, output is not visible
        }
      }
    }
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

  private inner class RunTestAction :
    AnAction("Run Test", "Run the snippet test", AllIcons.Actions.Execute) {
    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      executeTest(project, DefaultRunExecutor.getRunExecutorInstance())
    }
  }

  private inner class RunAllTestsAction :
    AnAction("Run All Tests", "Run all snippet tests", AllIcons.Actions.RunAll) {
    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      executeAllTests(project, DefaultRunExecutor.getRunExecutorInstance())
    }
  }

  private inner class DebugTestAction :
    AnAction("Debug Test", "Debug the snippet test", AllIcons.Actions.StartDebugger) {
    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      executeTest(project, DefaultDebugExecutor.getDebugExecutorInstance())
    }
  }

  private inner class OverwriteSnippetAction :
    AnAction(
      "Overwrite Snippet",
      "Run test and regenerate expected output",
      AllIcons.Actions.RerunAutomatically,
    ) {
    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return

      executeTest(
        project,
        DefaultRunExecutor.getRunExecutorInstance(),
        mapOf("OVERWRITE_SNIPPETS" to "1"),
      )
    }
  }

  /**
   * Builds a JUnit UniqueID selector for the snippet test. E.g.
   *
   * ```
   * [engine:LanguageSnippetTestsEngine]/[inputDirNode:lambdas]/[inputFileNode:lambdaStackTrace2.pkl]
   * ```
   */
  private fun buildUniqueId(file: VirtualFile): String? {
    val path = file.path
    // Pattern: .../LanguageSnippetTests/input/lambdas/lambdaStackTrace2.pkl
    val pattern = Regex(".*/([^/]+)/src/test/files/(\\w+)/input/(.+)$")
    val match = pattern.find(path) ?: return null

    val testType = match.groupValues[2] // e.g., "LanguageSnippetTests"
    val relativePath = match.groupValues[3] // e.g., "lambdas/lambdaStackTrace2.pkl"

    // Extract directory and filename
    val parts = relativePath.split("/")
    val fileName = parts.last()
    val engineName = testType + "Engine"
    val uniqueId = buildString {
      append("[engine:$engineName]")
      if (parts.size > 1) {
        for (dir in parts.dropLast(1)) {
          append("/[inputDirNode:$dir]")
        }
      }
      append("/[inputFileNode:$fileName]")
    }

    return uniqueId
  }

  private fun getTestClass(project: Project, file: VirtualFile): PsiClass {
    val path = file.path
    // Pattern: .../LanguageSnippetTests/input/lambdas/lambdaStackTrace2.pkl
    val pattern = Regex(".*/([^/]+)/src/test/files/(\\w+)/input/(.+)$")
    val match = pattern.find(path)!!
    val folder = match.groupValues[2]
    val className =
      when (folder) {
        "LanguageSnippetTests" -> "org.pkl.core.LanguageSnippetTests"
        "FormatterSnippetTests" -> "org.pkl.formatter.FormatterSnippetTests"
        // legacy; doesn't exist on main branch
        "SnippetTests" -> "org.pkl.server.SnippetTests"
        else -> throw IllegalStateException("")
      }
    return JavaPsiFacade.getInstance(project)
      .findClass(className, GlobalSearchScope.allScope(project))!!
  }

  private fun executeAllTests(project: Project, executor: Executor) {
    val file = inputEditor.file ?: return

    val path = file.path
    // Pattern: .../LanguageSnippetTests/input/lambdas/lambdaStackTrace2.pkl
    val pattern = Regex(".*/([^/]+)/src/test/files/(\\w+)/input/(.+)$")
    val match = pattern.find(path) ?: return

    val testType = match.groupValues[2] // e.g., "LanguageSnippetTests"
    executeTest(project, executor, testType) { data ->
      data.TEST_OBJECT = JUnitConfiguration.TEST_CLASS
      data.setMainClass(getTestClass(project, file))
    }
  }

  private fun executeTest(
    project: Project,
    executor: Executor,
    envVars: Map<String, String> = emptyMap(),
  ) {
    val file = inputEditor.file ?: return
    val uniqueId = buildUniqueId(file) ?: return
    executeTest(project, executor, file.name) { data ->
      data.TEST_OBJECT = JUnitConfiguration.TEST_UNIQUE_ID
      data.setUniqueIds(uniqueId)

      if (envVars.isNotEmpty()) {
        data.envs = envVars.toMutableMap()
        data.PASS_PARENT_ENVS = true
      }
    }
  }

  private fun executeTest(
    project: Project,
    executor: Executor,
    title: String,
    configure: (JUnitConfiguration.Data) -> Unit,
  ) {
    val file = inputEditor.file ?: return
    val module = ModuleUtil.findModuleForFile(file, project) ?: return

    val runManager = RunManager.getInstance(project)
    val configurationType = JUnitConfigurationType.getInstance()
    val configurationFactory = configurationType.configurationFactories.first()

    val settings = runManager.createConfiguration(title, configurationFactory)

    val configuration = settings.configuration as? JUnitConfiguration ?: return
    configure(configuration.persistentData)

    configuration.setModule(module)

    // Add the configuration to the RunManager so it appears in recent configurations
    runManager.addConfiguration(settings)
    runManager.selectedConfiguration = settings

    // Add listener to refresh output editor after test completes
    val messageBus = project.messageBus.connect()
    messageBus.subscribe(
      ExecutionManager.EXECUTION_TOPIC,
      object : ExecutionListener {
        override fun processTerminated(
          executorId: String,
          env: ExecutionEnvironment,
          handler: ProcessHandler,
          exitCode: Int,
        ) {
          // Check if this is our test run
          if (env.runProfile == configuration) {
            // Refresh the output editor after the test completes
            refreshOutputEditor(project)
            // Disconnect the listener after use
            messageBus.disconnect()
          }
        }
      },
    )

    ProgramRunnerUtil.executeConfiguration(settings, executor)
  }
}

data class SnippetTestSplitEditorState(
  val inputState: FileEditorState,
  val outputState: FileEditorState?,
) : FileEditorState {
  override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean {
    val other = otherState as? SnippetTestSplitEditorState ?: return false
    if (!inputState.canBeMergedWith(other.inputState, level)) return false
    val otherState = other.outputState
    return when {
      outputState == null && otherState == null -> true
      outputState != null && otherState != null -> outputState.canBeMergedWith(otherState, level)
      else -> false
    }
  }
}
