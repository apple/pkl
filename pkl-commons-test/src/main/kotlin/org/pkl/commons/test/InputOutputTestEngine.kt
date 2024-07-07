/**
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
package org.pkl.commons.test

import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.isRegularFile
import kotlin.io.path.useDirectoryEntries
import kotlin.reflect.KClass
import org.junit.platform.engine.*
import org.junit.platform.engine.TestDescriptor.Type
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.discovery.MethodSelector
import org.junit.platform.engine.discovery.PackageSelector
import org.junit.platform.engine.discovery.UniqueIdSelector
import org.junit.platform.engine.support.descriptor.*
import org.junit.platform.engine.support.hierarchical.EngineExecutionContext
import org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine
import org.junit.platform.engine.support.hierarchical.Node
import org.junit.platform.engine.support.hierarchical.Node.DynamicTestExecutor
import org.pkl.commons.toNormalizedPathString

abstract class InputOutputTestEngine :
  HierarchicalTestEngine<InputOutputTestEngine.ExecutionContext>() {
  protected val rootProjectDir = FileTestUtils.rootProjectDir

  protected abstract val testClass: KClass<*>

  protected open val includedTests: List<Regex> = listOf(Regex(".*"))

  @Suppress("RegExpUnexpectedAnchor")
  protected open val excludedTests: List<Regex> = listOf(Regex("$^"))

  protected abstract val inputDir: Path

  protected abstract val isInputFile: (Path) -> Boolean

  protected abstract fun expectedOutputFileFor(inputFile: Path): Path

  protected abstract fun generateOutputFor(inputFile: Path): Pair<Boolean, String>

  protected open fun beforeAll() {}

  protected open fun afterAll() {}

  class ExecutionContext : EngineExecutionContext

  override fun getId(): String = this::class.java.simpleName

  init {
    // Enforce consistent locale for tests to avoid inconsistent formatting.
    Locale.setDefault(Locale.ROOT)
  }

  override fun discover(
    discoveryRequest: EngineDiscoveryRequest,
    uniqueId: UniqueId
  ): TestDescriptor {
    val packageSelectors = discoveryRequest.getSelectorsByType(PackageSelector::class.java)
    val classSelectors = discoveryRequest.getSelectorsByType(ClassSelector::class.java)
    val methodSelectors = discoveryRequest.getSelectorsByType(MethodSelector::class.java)
    val uniqueIdSelectors = discoveryRequest.getSelectorsByType(UniqueIdSelector::class.java)

    val packageName = testClass.java.`package`.name
    val className = testClass.java.name

    if (
      methodSelectors.isEmpty() &&
        (packageSelectors.isEmpty() || packageSelectors.any { it.packageName == packageName }) &&
        (classSelectors.isEmpty() || classSelectors.any { it.className == className })
    ) {

      val rootNode =
        object : InputDirNode(uniqueId, inputDir, ClassSource.from(testClass.java)) {
          override fun before(context: ExecutionContext): ExecutionContext {
            beforeAll()
            return context
          }

          override fun after(context: ExecutionContext) {
            afterAll()
          }
        }
      return doDiscover(rootNode, uniqueIdSelectors)
    }

    // return empty descriptor w/o children
    return EngineDescriptor(uniqueId, javaClass.simpleName)
  }

  private fun doDiscover(
    dirNode: InputDirNode,
    uniqueIdSelectors: List<UniqueIdSelector>
  ): TestDescriptor {
    dirNode.inputDir.useDirectoryEntries { children ->
      for (child in children) {
        val testPath = child.toNormalizedPathString()
        val testName = child.fileName.toString()
        if (child.isRegularFile()) {
          if (
            isInputFile(child) &&
              includedTests.any { it.matches(testPath) } &&
              !excludedTests.any { it.matches(testPath) }
          ) {
            val childId = dirNode.uniqueId.append("inputFileNode", testName)
            if (
              uniqueIdSelectors.isEmpty() ||
                uniqueIdSelectors.any { childId.hasPrefix(it.uniqueId) }
            ) {
              dirNode.addChild(InputFileNode(childId, child))
            } // else skip
          }
        } else {
          val childId = dirNode.uniqueId.append("inputDirNode", testName)
          dirNode.addChild(
            doDiscover(
              InputDirNode(childId, child, DirectorySource.from(child.toFile())),
              uniqueIdSelectors
            )
          )
        }
      }
    }
    return dirNode
  }

  override fun createExecutionContext(request: ExecutionRequest) = ExecutionContext()

  private open inner class InputDirNode(
    uniqueId: UniqueId,
    val inputDir: Path,
    source: TestSource
  ) :
    AbstractTestDescriptor(uniqueId, inputDir.fileName.toString(), source), Node<ExecutionContext> {
    override fun getType() = Type.CONTAINER
  }

  private inner class InputFileNode(uniqueId: UniqueId, private val inputFile: Path) :
    AbstractTestDescriptor(
      uniqueId,
      inputFile.fileName.toString(),
      FileSource.from(inputFile.toFile())
    ),
    Node<ExecutionContext> {

    override fun getType() = Type.TEST

    override fun execute(
      context: ExecutionContext,
      dynamicTestExecutor: DynamicTestExecutor
    ): ExecutionContext {
      val (success, actualOutput) = generateOutputFor(inputFile)
      val expectedOutputFile = expectedOutputFileFor(inputFile)

      SnippetOutcome(expectedOutputFile, actualOutput, success).check()

      return context
    }
  }
}
