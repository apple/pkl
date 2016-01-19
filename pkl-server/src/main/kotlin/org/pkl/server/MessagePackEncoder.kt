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

import java.lang.RuntimeException
import kotlin.io.path.pathString
import org.msgpack.core.MessagePacker
import org.pkl.core.module.PathElement

internal class MessagePackEncoder(private val packer: MessagePacker) : MessageEncoder {
  private fun MessagePacker.packModuleReaderSpec(reader: ModuleReaderSpec) {
    packMapHeader(4)
    packKeyValue("scheme", reader.scheme)
    packKeyValue("hasHierarchicalUris", reader.hasHierarchicalUris)
    packKeyValue("isLocal", reader.isLocal)
    packKeyValue("isGlobbable", reader.isGlobbable)
  }

  private fun MessagePacker.packResourceReaderSpec(reader: ResourceReaderSpec) {
    packMapHeader(3)
    packKeyValue("scheme", reader.scheme)
    packKeyValue("hasHierarchicalUris", reader.hasHierarchicalUris)
    packKeyValue("isGlobbable", reader.isGlobbable)
  }

  private fun MessagePacker.packPathElement(pathElement: PathElement) {
    packMapHeader(2)
    packKeyValue("name", pathElement.name)
    packKeyValue("isDirectory", pathElement.isDirectory)
  }

  override fun encode(msg: Message) =
    with(packer) {
      packArrayHeader(2)
      packInt(msg.type.code)

      @Suppress("DuplicatedCode")
      when (msg.type.code) {
        MessageType.CREATE_EVALUATOR_REQUEST.code -> {
          msg as CreateEvaluatorRequest
          packMapHeader(8, msg.timeout, msg.rootDir, msg.cacheDir, msg.outputFormat)
          packKeyValue("requestId", msg.requestId)
          packKeyValue("allowedModules", msg.allowedModules?.map { it.toString() })
          packKeyValue("allowedResources", msg.allowedResources?.map { it.toString() })
          if (msg.clientModuleReaders != null) {
            packString("clientModuleReaders")
            packArrayHeader(msg.clientModuleReaders.size)
            for (moduleReader in msg.clientModuleReaders) {
              packModuleReaderSpec(moduleReader)
            }
          }
          if (msg.clientResourceReaders != null) {
            packString("clientResourceReaders")
            packArrayHeader(msg.clientResourceReaders.size)
            for (resourceReader in msg.clientResourceReaders) {
              packResourceReaderSpec(resourceReader)
            }
          }
          packKeyValue("modulePaths", msg.modulePaths?.map { it.pathString })
          packKeyValue("env", msg.env)
          packKeyValue("properties", msg.properties)
          packKeyValue("timeoutSeconds", msg.timeout?.toSeconds())
          packKeyValue("rootDir", msg.rootDir?.pathString)
          packKeyValue("cacheDir", msg.cacheDir?.pathString)
          packKeyValue("outputFormat", msg.outputFormat)
        }
        MessageType.CREATE_EVALUATOR_RESPONSE.code -> {
          msg as CreateEvaluatorResponse
          packMapHeader(1, msg.evaluatorId, msg.error)
          packKeyValue("requestId", msg.requestId)
          packKeyValue("evaluatorId", msg.evaluatorId)
          packKeyValue("error", msg.error)
        }
        MessageType.CLOSE_EVALUATOR.code -> {
          msg as CloseEvaluator
          packMapHeader(1)
          packKeyValue("evaluatorId", msg.evaluatorId)
        }
        MessageType.EVALUATE_REQUEST.code -> {
          msg as EvaluateRequest
          packMapHeader(3, msg.moduleText, msg.expr)
          packKeyValue("requestId", msg.requestId)
          packKeyValue("evaluatorId", msg.evaluatorId)
          packKeyValue("moduleUri", msg.moduleUri.toString())
          packKeyValue("moduleText", msg.moduleText)
          packKeyValue("expr", msg.expr)
        }
        MessageType.EVALUATE_RESPONSE.code -> {
          msg as EvaluateResponse
          packMapHeader(2, msg.result, msg.error)
          packKeyValue("requestId", msg.requestId)
          packKeyValue("evaluatorId", msg.evaluatorId)
          packKeyValue("result", msg.result)
          packKeyValue("error", msg.error)
        }
        MessageType.LOG_MESSAGE.code -> {
          msg as LogMessage
          packMapHeader(4)
          packKeyValue("evaluatorId", msg.evaluatorId)
          packKeyValue("level", msg.level)
          packKeyValue("message", msg.message)
          packKeyValue("frameUri", msg.frameUri)
        }
        MessageType.READ_RESOURCE_REQUEST.code -> {
          msg as ReadResourceRequest
          packMapHeader(3)
          packKeyValue("requestId", msg.requestId)
          packKeyValue("evaluatorId", msg.evaluatorId)
          packKeyValue("uri", msg.uri.toString())
        }
        MessageType.READ_RESOURCE_RESPONSE.code -> {
          msg as ReadResourceResponse
          packMapHeader(2, msg.contents, msg.error)
          packKeyValue("requestId", msg.requestId)
          packKeyValue("evaluatorId", msg.evaluatorId)
          packKeyValue("contents", msg.contents)
          packKeyValue("error", msg.error)
        }
        MessageType.READ_MODULE_REQUEST.code -> {
          msg as ReadModuleRequest
          packMapHeader(3)
          packKeyValue("requestId", msg.requestId)
          packKeyValue("evaluatorId", msg.evaluatorId)
          packKeyValue("uri", msg.uri.toString())
        }
        MessageType.READ_MODULE_RESPONSE.code -> {
          msg as ReadModuleResponse
          packMapHeader(2, msg.contents, msg.error)
          packKeyValue("requestId", msg.requestId)
          packKeyValue("evaluatorId", msg.evaluatorId)
          packKeyValue("contents", msg.contents)
          packKeyValue("error", msg.error)
        }
        MessageType.LIST_MODULES_REQUEST.code -> {
          msg as ListModulesRequest
          packMapHeader(3)
          packKeyValue("requestId", msg.requestId)
          packKeyValue("evaluatorId", msg.evaluatorId)
          packKeyValue("uri", msg.uri.toString())
        }
        MessageType.LIST_MODULES_RESPONSE.code -> {
          msg as ListModulesResponse
          packMapHeader(2, msg.pathElements, msg.error)
          packKeyValue("requestId", msg.requestId)
          packKeyValue("evaluatorId", msg.evaluatorId)
          if (msg.pathElements != null) {
            packString("pathElements")
            packArrayHeader(msg.pathElements.size)
            for (pathElement in msg.pathElements) {
              packPathElement(pathElement)
            }
          }
          packKeyValue("error", msg.error)
        }
        MessageType.LIST_RESOURCES_REQUEST.code -> {
          msg as ListResourcesRequest
          packMapHeader(3)
          packKeyValue("requestId", msg.requestId)
          packKeyValue("evaluatorId", msg.evaluatorId)
          packKeyValue("uri", msg.uri.toString())
        }
        MessageType.LIST_RESOURCES_RESPONSE.code -> {
          msg as ListResourcesResponse
          packMapHeader(2, msg.pathElements, msg.error)
          packKeyValue("requestId", msg.requestId)
          packKeyValue("evaluatorId", msg.evaluatorId)
          if (msg.pathElements != null) {
            packString("pathElements")
            packArrayHeader(msg.pathElements.size)
            for (pathElement in msg.pathElements) {
              packPathElement(pathElement)
            }
          }
          packKeyValue("error", msg.error)
        }
        else -> {
          throw RuntimeException("Missing encoding for ${msg.javaClass.simpleName}")
        }
      }

      flush()
    }

  private fun MessagePacker.packMapHeader(size: Int, value1: Any?, value2: Any?) =
    packMapHeader(size + (if (value1 != null) 1 else 0) + (if (value2 != null) 1 else 0))

  private fun MessagePacker.packMapHeader(
    size: Int,
    value1: Any?,
    value2: Any?,
    value3: Any?,
    value4: Any?
  ) =
    packMapHeader(
      size +
        (if (value1 != null) 1 else 0) +
        (if (value2 != null) 1 else 0) +
        (if (value3 != null) 1 else 0) +
        (if (value4 != null) 1 else 0)
    )

  private fun MessagePacker.packKeyValue(name: String, value: Int?) {
    if (value == null) return
    packString(name)
    packInt(value)
  }

  private fun MessagePacker.packKeyValue(name: String, value: Long?) {
    if (value == null) return
    packString(name)
    packLong(value)
  }

  private fun MessagePacker.packKeyValue(name: String, value: String?) {
    if (value == null) return
    packString(name)
    packString(value)
  }

  private fun MessagePacker.packKeyValue(name: String, value: Collection<String>?) {
    if (value == null) return
    packString(name)
    packArrayHeader(value.size)
    for (elem in value) packString(elem)
  }

  private fun MessagePacker.packKeyValue(name: String, value: Map<String, String>?) {
    if (value == null) return
    packString(name)
    packMapHeader(value.size)
    for ((k, v) in value) {
      packString(k)
      packString(v)
    }
  }

  private fun MessagePacker.packKeyValue(name: String, value: ByteArray?) {
    if (value == null) return
    packString(name)
    packBinaryHeader(value.size)
    writePayload(value)
  }

  private fun MessagePacker.packKeyValue(name: String, value: Boolean) {
    packString(name)
    packBoolean(value)
  }
}
