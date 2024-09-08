/*
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
package org.pkl.server

import java.io.IOException
import java.net.URI
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import kotlin.random.Random
import org.pkl.core.SecurityManager
import org.pkl.core.messaging.*
import org.pkl.core.module.PathElement
import org.pkl.core.resource.Resource
import org.pkl.core.resource.ResourceReader

/** Resource reader that delegates read logic to the client. */
internal class ClientResourceReader(
  private val transport: MessageTransport,
  private val evaluatorId: Long,
  private val readerSpec: Message.ResourceReaderSpec,
) : ResourceReader {
  private val readResponses: MutableMap<URI, Future<ByteArray>> = ConcurrentHashMap()

  private val listResources: MutableMap<URI, Future<List<PathElement>>> = ConcurrentHashMap()

  override fun hasHierarchicalUris(): Boolean = readerSpec.hasHierarchicalUris

  override fun isGlobbable(): Boolean = readerSpec.isGlobbable

  override fun getUriScheme() = readerSpec.scheme

  override fun read(uri: URI): Optional<Any> = Optional.of(Resource(uri, doRead(uri)))

  override fun hasElement(securityManager: SecurityManager, elementUri: URI): Boolean {
    securityManager.checkResolveResource(elementUri)
    return try {
      doRead(elementUri)
      true
    } catch (e: IOException) {
      false
    }
  }

  override fun listElements(securityManager: SecurityManager, baseUri: URI): List<PathElement> {
    securityManager.checkResolveResource(baseUri)
    return doListElements(baseUri)
  }

  private fun doListElements(baseUri: URI): List<PathElement> =
    listResources
      .computeIfAbsent(baseUri) {
        CompletableFuture<List<PathElement>>().apply {
          val request = Message.ListResourcesRequest(Random.nextLong(), evaluatorId, baseUri)
          transport.send(request) { response ->
            when (response) {
              is Message.ListResourcesResponse ->
                if (response.error != null) {
                  completeExceptionally(IOException(response.error))
                } else {
                  complete(response.pathElements ?: emptyList())
                }
              else -> completeExceptionally(ProtocolException("Unexpected response"))
            }
          }
        }
      }
      .getUnderlying()

  private fun doRead(uri: URI): ByteArray =
    readResponses
      .computeIfAbsent(uri) {
        CompletableFuture<ByteArray>().apply {
          val request = Message.ReadResourceRequest(Random.nextLong(), evaluatorId, uri)
          transport.send(request) { response ->
            when (response) {
              is Message.ReadResourceResponse -> {
                if (response.error != null) {
                  completeExceptionally(IOException(response.error))
                } else {
                  complete(response.contents)
                }
              }
              else -> {
                completeExceptionally(ProtocolException("Unexpected response: $response"))
              }
            }
          }
        }
      }
      .getUnderlying()
}
