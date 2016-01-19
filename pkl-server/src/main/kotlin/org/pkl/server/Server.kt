/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.server

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random
import org.pkl.core.*
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.module.ModuleKeyFactory
import org.pkl.core.module.ModulePathResolver
import org.pkl.core.project.Project
import org.pkl.core.resource.ResourceReader
import org.pkl.core.resource.ResourceReaders

class Server(private val transport: MessageTransport) : AutoCloseable {
  private val evaluators: MutableMap<Long, BinaryEvaluator> = ConcurrentHashMap()

  // https://github.com/jano7/executor would be the perfect executor here
  private val executor: ExecutorService = Executors.newSingleThreadExecutor()

  /** Starts listening to incoming messages */
  fun start() {
    transport.start(
      { message ->
        when (message) {
          is CloseEvaluator -> handleCloseEvaluator(message)
          else -> throw ProtocolException("Unexpected incoming one-way message: $message")
        }
      },
      { message ->
        when (message) {
          is CreateEvaluatorRequest -> handleCreateEvaluator(message)
          is EvaluateRequest -> handleEvaluate(message)
          else -> throw ProtocolException("Unexpected incoming request message: $message")
        }
      }
    )
  }

  /**
   * Stops listening to incoming messages, cancels pending evaluation requests, and releases
   * resources held by this server.
   */
  override fun close() {
    transport.closeQuietly()
    for ((_, evaluator) in evaluators) {
      // if currently in use, blocks until cancellation complete
      evaluator.closeQuietly()
    }
    executor.shutdown()
  }

  private fun handleCreateEvaluator(message: CreateEvaluatorRequest) {
    val evaluatorId = Random.Default.nextLong()
    val baseResponse = CreateEvaluatorResponse(message.requestId, evaluatorId = null, error = null)

    val evaluator =
      try {
        createEvaluator(message, evaluatorId)
      } catch (e: ServerException) {
        transport.send(baseResponse.copy(error = e.message))
        return
      }

    evaluators[evaluatorId] = evaluator
    transport.send(baseResponse.copy(evaluatorId = evaluatorId))
  }

  private fun handleEvaluate(msg: EvaluateRequest) {
    val baseResponse = EvaluateResponse(msg.requestId, msg.evaluatorId, result = null, error = null)

    val evaluator = evaluators[msg.evaluatorId]
    if (evaluator == null) {
      transport.send(
        baseResponse.copy(error = "Evaluator with ID ${msg.evaluatorId} was not found.")
      )
      return
    }

    executor.execute {
      try {
        val resp = evaluator.evaluate(ModuleSource.create(msg.moduleUri, msg.moduleText), msg.expr)
        transport.send(baseResponse.copy(result = resp))
      } catch (e: PklBugException) {
        transport.send(baseResponse.copy(error = e.toString()))
      } catch (e: PklException) {
        transport.send(baseResponse.copy(error = e.message))
      }
    }
  }

  private fun handleCloseEvaluator(message: CloseEvaluator) {
    val evaluator = evaluators.remove(message.evaluatorId)
    if (evaluator == null) {
      log("Ignoring close request for unknown evaluator ID `${message.evaluatorId}`.")
      return
    }
    evaluator.close()
  }

  private fun createEvaluator(message: CreateEvaluatorRequest, evaluatorId: Long): BinaryEvaluator {
    val project =
      message.projectDir?.let { dir ->
        val securityManager =
          SecurityManagers.standard(
            message.allowedModules ?: SecurityManagers.defaultAllowedModules,
            message.allowedResources ?: SecurityManagers.defaultAllowedResources,
            SecurityManagers.defaultTrustLevels,
            message.rootDir
          )
        Project.loadFromPath(dir, securityManager, message.timeout)
      }
    val projectSettings = if (message.disableProjectSettings == true) null else project?.settings
    val modulePaths = message.modulePaths ?: projectSettings?.modulePath ?: emptyList()
    val resolver = ModulePathResolver(modulePaths)
    val allowedModules = message.allowedModules ?: projectSettings?.allowedModules ?: emptyList()
    val allowedResources =
      message.allowedResources ?: projectSettings?.allowedResources ?: emptyList()
    val rootDir = message.rootDir ?: projectSettings?.rootDir
    val env = message.env ?: projectSettings?.env ?: emptyMap()
    val properties = message.properties ?: projectSettings?.externalProperties ?: emptyMap()
    val timeout = message.timeout ?: projectSettings?.timeout?.toJavaDuration()
    val cacheDir = message.cacheDir ?: projectSettings?.moduleCacheDir
    return BinaryEvaluator(
      StackFrameTransformers.defaultTransformer,
      SecurityManagers.standard(
        allowedModules,
        allowedResources,
        SecurityManagers.defaultTrustLevels,
        rootDir
      ),
      ClientLogger(evaluatorId, transport),
      createModuleKeyFactories(message, evaluatorId, resolver),
      createResourceReaders(message, evaluatorId, resolver),
      env,
      properties,
      timeout,
      cacheDir,
      project?.dependencies,
      message.outputFormat
    )
  }

  private fun createResourceReaders(
    message: CreateEvaluatorRequest,
    evaluatorId: Long,
    modulePathResolver: ModulePathResolver
  ): List<ResourceReader> = buildList {
    add(ResourceReaders.environmentVariable())
    add(ResourceReaders.externalProperty())
    add(ResourceReaders.file())
    add(ResourceReaders.http())
    add(ResourceReaders.https())
    add(ResourceReaders.pkg())
    add(ResourceReaders.projectpackage())
    add(ResourceReaders.modulePath(modulePathResolver))
    // add client-side resource readers last to ensure they win over builtin ones
    for (readerSpec in message.clientResourceReaders ?: emptyList()) {
      val resourceReader = ClientResourceReader(transport, evaluatorId, readerSpec)
      add(resourceReader)
    }
  }

  private fun createModuleKeyFactories(
    message: CreateEvaluatorRequest,
    evaluatorId: Long,
    modulePathResolver: ModulePathResolver
  ): List<ModuleKeyFactory> = buildList {
    // add client-side module key factory first to ensure it wins over builtin ones
    if (message.clientModuleReaders?.isNotEmpty() == true) {
      add(ClientModuleKeyFactory(message.clientModuleReaders, transport, evaluatorId))
    }
    add(ModuleKeyFactories.standardLibrary)
    addAll(ModuleKeyFactories.fromServiceProviders())
    add(ModuleKeyFactories.file)
    add(ModuleKeyFactories.modulePath(modulePathResolver))
    add(ModuleKeyFactories.pkg)
    add(ModuleKeyFactories.projectpackage)
    add(ModuleKeyFactories.genericUrl)
  }
}
