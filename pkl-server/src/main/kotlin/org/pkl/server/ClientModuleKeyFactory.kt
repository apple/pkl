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

import java.io.IOException
import java.net.URI
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import kotlin.random.Random
import org.pkl.core.SecurityManager
import org.pkl.core.module.ModuleKey
import org.pkl.core.module.ModuleKeyFactory
import org.pkl.core.module.PathElement
import org.pkl.core.module.ResolvedModuleKey
import org.pkl.core.module.ResolvedModuleKeys

internal class ClientModuleKeyFactory(
  private val readerSpecs: Collection<ModuleReaderSpec>,
  transport: MessageTransport,
  evaluatorId: Long
) : ModuleKeyFactory {
  companion object {
    private class ClientModuleKeyResolver(
      private val transport: MessageTransport,
      private val evaluatorId: Long,
    ) {
      private val readResponses: MutableMap<URI, Future<String>> = ConcurrentHashMap()

      private val listResponses: MutableMap<URI, Future<List<PathElement>>> = ConcurrentHashMap()

      fun listElements(securityManager: SecurityManager, uri: URI): List<PathElement> {
        securityManager.checkResolveModule(uri)
        return doListElements(uri)
      }

      fun hasElement(securityManager: SecurityManager, uri: URI): Boolean {
        securityManager.checkResolveModule(uri)
        return try {
          doReadModule(uri)
          true
        } catch (e: IOException) {
          false
        }
      }

      fun resolveModule(securityManager: SecurityManager, uri: URI): String {
        securityManager.checkResolveModule(uri)
        return doReadModule(uri)
      }

      private fun doReadModule(uri: URI): String =
        readResponses
          .computeIfAbsent(uri) {
            CompletableFuture<String>().apply {
              val request = ReadModuleRequest(Random.nextLong(), evaluatorId, uri)
              transport.send(request) { response ->
                when (response) {
                  is ReadModuleResponse -> {
                    if (response.error != null) {
                      completeExceptionally(IOException(response.error))
                    } else {
                      complete(response.contents!!)
                    }
                  }
                  else -> {
                    completeExceptionally(ProtocolException("unexpected response"))
                  }
                }
              }
            }
          }
          .getUnderlying()

      private fun doListElements(uri: URI): List<PathElement> =
        listResponses
          .computeIfAbsent(uri) {
            CompletableFuture<List<PathElement>>().apply {
              val request = ListModulesRequest(Random.nextLong(), evaluatorId, uri)
              transport.send(request) { response ->
                when (response) {
                  is ListModulesResponse -> {
                    if (response.error != null) {
                      completeExceptionally(IOException(response.error))
                    } else {
                      complete(response.pathElements!!)
                    }
                  }
                  else -> completeExceptionally(ProtocolException("unexpected response"))
                }
              }
            }
          }
          .getUnderlying()
    }

    /** [ModuleKey] that delegates module reads to the client. */
    private class ClientModuleKey(
      private val uri: URI,
      private val spec: ModuleReaderSpec,
      private val resolver: ClientModuleKeyResolver,
    ) : ModuleKey {
      override fun isLocal(): Boolean = spec.isLocal

      override fun hasHierarchicalUris(): Boolean = spec.hasHierarchicalUris

      override fun isGlobbable(): Boolean = spec.isGlobbable

      override fun getUri(): URI = uri

      override fun listElements(securityManager: SecurityManager, baseUri: URI): List<PathElement> =
        resolver.listElements(securityManager, baseUri)

      override fun resolve(securityManager: SecurityManager): ResolvedModuleKey {
        val contents = resolver.resolveModule(securityManager, uri)
        return ResolvedModuleKeys.virtual(this, uri, contents, true)
      }

      override fun hasElement(securityManager: SecurityManager, uri: URI): Boolean {
        return resolver.hasElement(securityManager, uri)
      }
    }
  }

  private val schemes = readerSpecs.map { it.scheme }

  private val resolver: ClientModuleKeyResolver = ClientModuleKeyResolver(transport, evaluatorId)

  override fun create(uri: URI): Optional<ModuleKey> =
    when (uri.scheme) {
      in schemes -> {
        val readerSpec = readerSpecs.find { it.scheme == uri.scheme }!!
        val moduleKey = ClientModuleKey(uri, readerSpec, resolver)
        Optional.of(moduleKey)
      }
      else -> Optional.empty()
    }
}
