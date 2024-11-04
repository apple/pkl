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
package org.pkl.core.externalreader

import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import kotlin.random.Random
import org.pkl.core.externalreader.ReaderMessages.*
import org.pkl.core.messaging.MessageTransport
import org.pkl.core.messaging.MessageTransports
import org.pkl.core.messaging.ProtocolException

class TestReaderProcess(private val transport: MessageTransport) : ReaderProcess {
  private val initializeModuleReaderResponses: MutableMap<String, Future<ModuleReaderSpec?>> =
    ConcurrentHashMap()
  private val initializeResourceReaderResponses: MutableMap<String, Future<ResourceReaderSpec?>> =
    ConcurrentHashMap()

  override fun close() {
    transport.send(CloseExternalProcess())
    transport.close()
  }

  override fun getModuleResolver(evaluatorId: Long): ModuleResolver =
    ModuleResolver.of(transport, evaluatorId)

  override fun getResourceResolver(evaluatorId: Long): ResourceResolver =
    ResourceResolver.of(transport, evaluatorId)

  fun run() {
    try {
      transport.start(
        { throw ProtocolException("Unexpected incoming one-way message: $it") },
        { throw ProtocolException("Unexpected incoming request message: $it") },
      )
    } catch (e: ProtocolException) {
      throw RuntimeException(e)
    } catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  override fun getModuleReaderSpec(scheme: String): ModuleReaderSpec? =
    initializeModuleReaderResponses
      .computeIfAbsent(scheme) {
        CompletableFuture<ModuleReaderSpec?>().apply {
          val request = InitializeModuleReaderRequest(Random.nextLong(), scheme)
          transport.send(request) { response ->
            when (response) {
              is InitializeModuleReaderResponse -> {
                val spec =
                  response.spec?.let {
                    ModuleReaderSpec(it.scheme, it.hasHierarchicalUris, it.isLocal, it.isGlobbable)
                  }
                complete(spec)
              }
              else -> completeExceptionally(ProtocolException("unexpected response"))
            }
          }
        }
      }
      .getUnderlying()

  override fun getResourceReaderSpec(scheme: String): ResourceReaderSpec? =
    initializeResourceReaderResponses
      .computeIfAbsent(scheme) {
        CompletableFuture<ResourceReaderSpec?>().apply {
          val request = InitializeResourceReaderRequest(Random.nextLong(), scheme)
          transport.send(request) { response ->
            when (response) {
              is InitializeResourceReaderResponse -> {
                val spec =
                  response.spec?.let {
                    ResourceReaderSpec(it.scheme, it.hasHierarchicalUris, it.isGlobbable)
                  }
                complete(spec)
              }
              else -> completeExceptionally(ProtocolException("unexpected response"))
            }
          }
        }
      }
      .getUnderlying()

  companion object {
    fun initializeTestHarness(
      moduleReaders: List<ModuleReader>,
      resourceReaders: List<ResourceReader>,
    ): Pair<TestReaderProcess, ReaderRuntime> {
      val rxIn = PipedInputStream(10240)
      val rxOut = PipedOutputStream(rxIn)
      val txIn = PipedInputStream(10240)
      val txOut = PipedOutputStream(txIn)
      val serverTransport =
        MessageTransports.stream(MessagePackDecoder(rxIn), MessagePackEncoder(txOut)) {}
      val clientTransport =
        MessageTransports.stream(MessagePackDecoder(txIn), MessagePackEncoder(rxOut)) {}

      val runtime = ReaderRuntime(moduleReaders, resourceReaders, clientTransport)
      val proc = TestReaderProcess(serverTransport)

      Thread(runtime::run).start()
      Thread(proc::run).start()

      return proc to runtime
    }
  }
}

fun <T> Future<T>.getUnderlying(): T =
  try {
    get()
  } catch (e: ExecutionException) {
    throw e.cause!!
  }
