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
import org.pkl.core.externalreader.ReaderMessages.*
import org.pkl.core.messaging.Message
import org.pkl.core.messaging.MessageTransport
import org.pkl.core.messaging.Messages
import org.pkl.core.messaging.Messages.*
import org.pkl.core.messaging.ProtocolException
import org.pkl.core.util.Nullable

/** An implementation of the client side of the external reader flow */
class ReaderRuntime(
  private val moduleReaders: List<ModuleReader>,
  private val resourceReaders: List<ResourceReader>,
  private val transport: MessageTransport,
) {
  /** Close the runtime and its transport. */
  fun close() {
    transport.close()
  }

  private fun findModuleReader(scheme: String): @Nullable ModuleReader? {
    for (moduleReader in moduleReaders) {
      if (moduleReader.scheme.equals(scheme, ignoreCase = true)) {
        return moduleReader
      }
    }
    return null
  }

  private fun findResourceReader(scheme: String): @Nullable ResourceReader? {
    for (resourceReader in resourceReaders) {
      if (resourceReader.scheme.equals(scheme, ignoreCase = true)) {
        return resourceReader
      }
    }
    return null
  }

  /**
   * Start the runtime so it can respond to incoming messages on its transport.
   *
   * Blocks until the underlying transport is closed.
   */
  @Throws(ProtocolException::class, IOException::class)
  fun run() {
    transport.start(
      { msg: Message.OneWay ->
        if (msg.type() == Message.Type.CLOSE_EXTERNAL_PROCESS) {
          close()
        } else {
          throw ProtocolException("Unexpected incoming one-way message: $msg")
        }
      },
      { msg: Message.Request ->
        when (msg.type()) {
          Message.Type.INITIALIZE_MODULE_READER_REQUEST -> {
            val req = msg as InitializeModuleReaderRequest
            val reader = findModuleReader(req.scheme)
            var spec: Messages.ModuleReaderSpec? = null
            if (reader != null) {
              spec = reader.spec
            }
            transport.send(InitializeModuleReaderResponse(req.requestId, spec))
          }
          Message.Type.INITIALIZE_RESOURCE_READER_REQUEST -> {
            val req = msg as InitializeResourceReaderRequest
            val reader = findResourceReader(req.scheme)
            var spec: Messages.ResourceReaderSpec? = null
            if (reader != null) {
              spec = reader.spec
            }
            transport.send(InitializeResourceReaderResponse(req.requestId, spec))
          }
          Message.Type.LIST_MODULES_REQUEST -> {
            val req = msg as ListModulesRequest
            val reader = findModuleReader(req.uri.scheme)
            if (reader == null) {
              transport.send(
                ListModulesResponse(
                  req.requestId,
                  req.evaluatorId,
                  null,
                  "No module reader found for scheme " + req.uri.scheme,
                )
              )
              return@start
            }
            try {
              transport.send(
                ListModulesResponse(
                  req.requestId,
                  req.evaluatorId,
                  reader.listElements(req.uri),
                  null,
                )
              )
            } catch (e: Exception) {
              transport.send(
                ListModulesResponse(req.requestId, req.evaluatorId, null, e.toString())
              )
            }
          }
          Message.Type.LIST_RESOURCES_REQUEST -> {
            val req = msg as ListResourcesRequest
            val reader = findModuleReader(req.uri.scheme)
            if (reader == null) {
              transport.send(
                ListResourcesResponse(
                  req.requestId,
                  req.evaluatorId,
                  null,
                  "No resource reader found for scheme " + req.uri.scheme,
                )
              )
              return@start
            }
            try {
              transport.send(
                ListResourcesResponse(
                  req.requestId,
                  req.evaluatorId,
                  reader.listElements(req.uri),
                  null,
                )
              )
            } catch (e: Exception) {
              transport.send(
                ListResourcesResponse(req.requestId, req.evaluatorId, null, e.toString())
              )
            }
          }
          Message.Type.READ_MODULE_REQUEST -> {
            val req = msg as ReadModuleRequest
            val reader = findModuleReader(req.uri.scheme)
            if (reader == null) {
              transport.send(
                ReadModuleResponse(
                  req.requestId,
                  req.evaluatorId,
                  null,
                  "No module reader found for scheme " + req.uri.scheme,
                )
              )
              return@start
            }
            try {
              transport.send(
                ReadModuleResponse(req.requestId, req.evaluatorId, reader.read(req.uri), null)
              )
            } catch (e: Exception) {
              transport.send(ReadModuleResponse(req.requestId, req.evaluatorId, null, e.toString()))
            }
          }
          Message.Type.READ_RESOURCE_REQUEST -> {
            val req = msg as ReadResourceRequest
            val reader = findResourceReader(req.uri.scheme)
            if (reader == null) {
              transport.send(
                ReadResourceResponse(
                  req.requestId,
                  req.evaluatorId,
                  byteArrayOf(),
                  "No resource reader found for scheme " + req.uri.scheme,
                )
              )
              return@start
            }
            try {
              transport.send(
                ReadResourceResponse(req.requestId, req.evaluatorId, reader.read(req.uri), null)
              )
            } catch (e: Exception) {
              transport.send(
                ReadResourceResponse(req.requestId, req.evaluatorId, byteArrayOf(), e.toString())
              )
            }
          }
          else -> throw ProtocolException("Unexpected incoming request message: $msg")
        }
      },
    )
  }
}
