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
package org.pkl.server

import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern
import kotlin.random.Random
import org.pkl.core.*
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings
import org.pkl.core.externalreader.ExternalReaderProcess
import org.pkl.core.externalreader.ExternalResourceResolver
import org.pkl.core.externalreader.ModuleReaderSpec
import org.pkl.core.externalreader.ResourceReaderSpec
import org.pkl.core.http.HttpClient
import org.pkl.core.messaging.MessageTransport
import org.pkl.core.messaging.MessageTransports
import org.pkl.core.messaging.ProtocolException
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.module.ModuleKeyFactory
import org.pkl.core.module.ModulePathResolver
import org.pkl.core.packages.PackageUri
import org.pkl.core.project.DeclaredDependencies
import org.pkl.core.resource.ResourceReader
import org.pkl.core.resource.ResourceReaders
import org.pkl.core.util.IoUtils

class Server(private val transport: MessageTransport) : AutoCloseable {
  private val evaluators: MutableMap<Long, BinaryEvaluator> = ConcurrentHashMap()

  // https://github.com/jano7/executor would be the perfect executor here
  private val executor: ExecutorService = Executors.newSingleThreadExecutor()

  // ExternalProcess instances with the same ExternalReader spec are shared per evaluator
  private val externalReaderProcesses:
    MutableMap<Long, MutableMap<ExternalReader, ExternalReaderProcess>> =
    ConcurrentHashMap()

  companion object {
    fun stream(inputStream: InputStream, outputStream: OutputStream): Server =
      Server(
        MessageTransports.stream(
          ServerMessagePackDecoder(inputStream),
          ServerMessagePackEncoder(outputStream),
          ::log,
        )
      )
  }

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
      },
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
    val baseResponse = CreateEvaluatorResponse(message.requestId(), null, null)

    val evaluator =
      try {
        createEvaluator(message, evaluatorId)
      } catch (e: ProtocolException) {
        transport.send(baseResponse.copy(error = e.message ?: ""))
        return
      }

    evaluators[evaluatorId] = evaluator
    transport.send(baseResponse.copy(evaluatorId = evaluatorId))
  }

  private fun handleEvaluate(msg: EvaluateRequest) {
    val baseResponse = EvaluateResponse(msg.requestId(), msg.evaluatorId, null, null)

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
        transport.send(baseResponse.copy(error = e.message ?: ""))
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

    // close any running ExternalProcess instances for the closed evaluator
    externalReaderProcesses[message.evaluatorId]?.values?.forEach { it.close() }
  }

  private fun buildDeclaredDependencies(
    projectFileUri: URI,
    dependencies: Map<String, Dependency>,
    myPackageUri: URI?,
  ): DeclaredDependencies {
    val remoteDependencies = buildMap {
      for ((key, dep) in dependencies) {
        if (dep is RemoteDependency) {
          put(
            key,
            org.pkl.core.packages.Dependency.RemoteDependency(
              PackageUri(dep.packageUri),
              dep.checksums,
            ),
          )
        }
      }
    }
    val localDependencies = buildMap {
      for ((key, dep) in dependencies) {
        if (dep is Project) {
          val localDep =
            buildDeclaredDependencies(dep.projectFileUri, dep.dependencies, dep.packageUri)
          put(key, localDep)
        }
      }
    }
    return DeclaredDependencies(
      remoteDependencies,
      localDependencies,
      projectFileUri,
      myPackageUri?.let(::PackageUri),
    )
  }

  private fun createEvaluator(message: CreateEvaluatorRequest, evaluatorId: Long): BinaryEvaluator {
    val modulePaths = message.modulePaths ?: emptyList()
    val resolver = ModulePathResolver(modulePaths)
    val allowedModules = message.allowedModules?.map { Pattern.compile(it) } ?: emptyList()
    val allowedResources = message.allowedResources?.map { Pattern.compile(it) } ?: emptyList()
    val rootDir = message.rootDir
    val env = message.env ?: emptyMap()
    val properties = message.properties ?: emptyMap()
    val timeout = message.timeout
    val cacheDir = message.cacheDir
    val httpClient =
      with(HttpClient.builder()) {
        message.http?.proxy?.let { proxy ->
          setProxy(proxy.address, proxy.noProxy ?: listOf())
          proxy.address?.let(IoUtils::setSystemProxy)
          proxy.noProxy?.let { System.setProperty("http.nonProxyHosts", it.joinToString("|")) }
        }
        message.http?.caCertificates?.let(::addCertificates)
        buildLazily()
      }
    val dependencies =
      message.project?.let { proj ->
        buildDeclaredDependencies(proj.projectFileUri, proj.dependencies, null)
      }
    log("Got dependencies: $dependencies")
    return BinaryEvaluator(
      StackFrameTransformers.defaultTransformer,
      SecurityManagers.standard(
        allowedModules,
        allowedResources,
        SecurityManagers.defaultTrustLevels,
        rootDir,
      ),
      httpClient,
      ClientLogger(evaluatorId, transport),
      createModuleKeyFactories(message, evaluatorId, resolver),
      createResourceReaders(message, evaluatorId, resolver),
      env,
      properties,
      timeout,
      cacheDir,
      dependencies,
      message.outputFormat,
    )
  }

  private fun createResourceReaders(
    message: CreateEvaluatorRequest,
    evaluatorId: Long,
    modulePathResolver: ModulePathResolver,
  ): List<ResourceReader> = buildList {
    add(ResourceReaders.environmentVariable())
    add(ResourceReaders.externalProperty())
    add(ResourceReaders.file())
    add(ResourceReaders.http())
    add(ResourceReaders.https())
    add(ResourceReaders.pkg())
    add(ResourceReaders.projectpackage())
    add(ResourceReaders.modulePath(modulePathResolver))
    for ((scheme, spec) in message.externalResourceReaders ?: emptyMap()) {
      add(
        ResourceReaders.externalProcess(scheme, getExternalProcess(evaluatorId, spec), evaluatorId)
      )
    }
    // add client-side resource readers last to ensure they win over builtin ones
    for (readerSpec in message.clientResourceReaders ?: emptyList()) {
      add(
        ResourceReaders.externalResolver(
          ResourceReaderSpec(
            readerSpec.scheme,
            readerSpec.hasHierarchicalUris,
            readerSpec.isGlobbable,
          ),
          ExternalResourceResolver.of(transport, evaluatorId),
        )
      )
    }
  }

  private fun createModuleKeyFactories(
    message: CreateEvaluatorRequest,
    evaluatorId: Long,
    modulePathResolver: ModulePathResolver,
  ): List<ModuleKeyFactory> = buildList {
    // add client-side module key factory first to ensure it wins over builtin ones
    if (message.clientModuleReaders?.isNotEmpty() == true) {
      val readerSpecs =
        message.clientModuleReaders.map {
          ModuleReaderSpec(it.scheme, it.hasHierarchicalUris, it.isLocal, it.isGlobbable)
        }
      add(ClientModuleKeyFactory(readerSpecs, transport, evaluatorId))
    }
    for ((scheme, spec) in message.externalModuleReaders ?: emptyMap()) {
      add(
        ModuleKeyFactories.externalProcess(
          scheme,
          getExternalProcess(evaluatorId, spec),
          evaluatorId,
        )
      )
    }
    add(ModuleKeyFactories.standardLibrary)
    addAll(ModuleKeyFactories.fromServiceProviders())
    add(ModuleKeyFactories.file)
    add(ModuleKeyFactories.modulePath(modulePathResolver))
    add(ModuleKeyFactories.pkg)
    add(ModuleKeyFactories.projectpackage)
    add(ModuleKeyFactories.http)
    add(ModuleKeyFactories.genericUrl)
  }

  private fun getExternalProcess(evaluatorId: Long, spec: ExternalReader): ExternalReaderProcess =
    externalReaderProcesses
      .computeIfAbsent(evaluatorId) { ConcurrentHashMap() }
      .computeIfAbsent(spec) {
        ExternalReaderProcess.of(PklEvaluatorSettings.ExternalReader(it.executable, it.arguments))
      }
}
