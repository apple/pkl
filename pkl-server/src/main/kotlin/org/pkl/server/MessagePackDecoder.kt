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
package org.pkl.server

import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.util.regex.Pattern
import org.msgpack.core.MessageTypeException
import org.msgpack.core.MessageUnpacker
import org.msgpack.value.Value
import org.msgpack.value.impl.ImmutableStringValueImpl
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings
import org.pkl.core.module.PathElement
import org.pkl.core.packages.Checksums

internal class MessagePackDecoder(private val unpacker: MessageUnpacker) : MessageDecoder {
  override fun decode(): Message? {
    if (!unpacker.hasNext()) return null

    val code =
      try {
        val arraySize = unpacker.unpackArrayHeader()
        if (arraySize != 2) {
          throw DecodeException("Malformed message header (expected size 2, but got $arraySize).")
        }
        unpacker.unpackInt()
      } catch (e: MessageTypeException) {
        throw DecodeException("Malformed message header.", e)
      }

    return try {
      val map = unpacker.unpackValue().asMapValue().map()
      when (code) {
        MessageType.CREATE_EVALUATOR_REQUEST.code -> {
          CreateEvaluatorRequest(
            requestId = map.get("requestId").asIntegerValue().asLong(),
            allowedModules = map.unpackStringListOrNull("allowedModules")?.map(Pattern::compile),
            allowedResources =
              map.unpackStringListOrNull("allowedResources")?.map(Pattern::compile),
            clientModuleReaders = map.unpackModuleReaderSpec("clientModuleReaders"),
            clientResourceReaders = map.unpackResourceReaderSpec("clientResourceReaders"),
            modulePaths = map.unpackStringListOrNull("modulePaths")?.map(Path::of),
            env = map.unpackStringMapOrNull("env"),
            properties = map.unpackStringMapOrNull("properties"),
            timeout = map.unpackLongOrNull("timeoutSeconds")?.let(Duration::ofSeconds),
            rootDir = map.unpackStringOrNull("rootDir")?.let(Path::of),
            cacheDir = map.unpackStringOrNull("cacheDir")?.let(Path::of),
            outputFormat = map.unpackStringOrNull("outputFormat"),
            project = map.unpackProject("project"),
            http = map.unpackHttp("http"),
          )
        }
        MessageType.CREATE_EVALUATOR_RESPONSE.code -> {
          CreateEvaluatorResponse(
            requestId = map.unpackLong("requestId"),
            evaluatorId = map.unpackLongOrNull("evaluatorId"),
            error = map.unpackStringOrNull("error")
          )
        }
        MessageType.CLOSE_EVALUATOR.code -> {
          CloseEvaluator(evaluatorId = map.unpackLong("evaluatorId"))
        }
        MessageType.EVALUATE_REQUEST.code -> {
          EvaluateRequest(
            requestId = map.unpackLong("requestId"),
            evaluatorId = map.unpackLong("evaluatorId"),
            moduleUri = map.unpackString("moduleUri").let(::URI),
            moduleText = map.unpackStringOrNull("moduleText"),
            expr = map.unpackStringOrNull("expr")
          )
        }
        MessageType.EVALUATE_RESPONSE.code -> {
          EvaluateResponse(
            requestId = map.unpackLong("requestId"),
            evaluatorId = map.unpackLong("evaluatorId"),
            result = map.unpackByteArrayOrNull("result"),
            error = map.unpackStringOrNull("error")
          )
        }
        MessageType.LOG_MESSAGE.code -> {
          LogMessage(
            evaluatorId = map.unpackLong("evaluatorId"),
            level = map.unpackIntValue("level"),
            message = map.unpackString("message"),
            frameUri = map.unpackString("frameUri")
          )
        }
        MessageType.READ_RESOURCE_REQUEST.code -> {
          ReadResourceRequest(
            requestId = map.unpackLong("requestId"),
            evaluatorId = map.unpackLong("evaluatorId"),
            uri = map.unpackString("uri").let(::URI)
          )
        }
        MessageType.READ_RESOURCE_RESPONSE.code -> {
          ReadResourceResponse(
            requestId = map.unpackLong("requestId"),
            evaluatorId = map.unpackLong("evaluatorId"),
            contents = map.unpackByteArrayOrNull("contents"),
            error = map.unpackStringOrNull("error")
          )
        }
        MessageType.READ_MODULE_REQUEST.code -> {
          ReadModuleRequest(
            requestId = map.unpackLong("requestId"),
            evaluatorId = map.unpackLong("evaluatorId"),
            uri = map.unpackString("uri").let(::URI)
          )
        }
        MessageType.READ_MODULE_RESPONSE.code -> {
          ReadModuleResponse(
            requestId = map.unpackLong("requestId"),
            evaluatorId = map.unpackLong("evaluatorId"),
            contents = map.unpackStringOrNull("contents"),
            error = map.unpackStringOrNull("error")
          )
        }
        MessageType.LIST_MODULES_REQUEST.code -> {
          ListModulesRequest(
            requestId = map.unpackLong("requestId"),
            evaluatorId = map.unpackLong("evaluatorId"),
            uri = map.unpackString("uri").let(::URI)
          )
        }
        MessageType.LIST_MODULES_RESPONSE.code -> {
          ListModulesResponse(
            requestId = map.unpackLong("requestId"),
            evaluatorId = map.unpackLong("evaluatorId"),
            pathElements = map.unpackPathElements("pathElements"),
            error = map.unpackStringOrNull("error")
          )
        }
        MessageType.LIST_RESOURCES_REQUEST.code -> {
          ListResourcesRequest(
            requestId = map.unpackLong("requestId"),
            evaluatorId = map.unpackLong("evaluatorId"),
            uri = map.unpackString("uri").let(::URI)
          )
        }
        MessageType.LIST_RESOURCES_RESPONSE.code -> {
          ListResourcesResponse(
            requestId = map.unpackLong("requestId"),
            evaluatorId = map.unpackLong("evaluatorId"),
            pathElements = map.unpackPathElements("pathElements"),
            error = map.unpackStringOrNull("error")
          )
        }
        else -> throw ProtocolException("Invalid message code: $code")
      }
    } catch (e: MessageTypeException) {
      throw DecodeException("Malformed message body for message with code `$code`.", e)
    }
  }

  private fun Array<Value>.unpackValueOrNull(key: String): Value? {
    for (i in indices.step(2)) {
      val currKey = this[i].asStringValue().asString()
      if (currKey == key) return this[i + 1]
    }
    return null
  }

  private fun Map<Value, Value>.getNullable(key: String): Value? =
    this[ImmutableStringValueImpl(key)]

  private fun Map<Value, Value>.get(key: String): Value =
    getNullable(key) ?: throw DecodeException("Missing message parameter `$key`")

  private fun Array<Value>.unpackValue(key: String): Value =
    unpackValueOrNull(key) ?: throw DecodeException("Missing message parameter `$key`.")

  private fun Map<Value, Value>.unpackStringListOrNull(key: String): List<String>? {
    val value = getNullable(key) ?: return null
    return value.asArrayValue().map { it.asStringValue().asString() }
  }

  private fun Map<Value, Value>.unpackStringMapOrNull(key: String): Map<String, String>? {
    val value = getNullable(key) ?: return null
    return value.asMapValue().entrySet().associate { (k, v) ->
      k.asStringValue().asString() to v.asStringValue().asString()
    }
  }

  private fun Map<Value, Value>.unpackLong(key: String): Long = get(key).asIntegerValue().asLong()

  private fun Map<Value, Value>.unpackBoolean(key: String): Boolean =
    get(key).asBooleanValue().boolean

  private fun Map<Value, Value>.unpackBooleanOrNull(key: String): Boolean? =
    getNullable(key)?.asBooleanValue()?.boolean

  private fun Map<Value, Value>.unpackLongOrNull(key: String): Long? =
    getNullable(key)?.asIntegerValue()?.asLong()

  private fun Map<Value, Value>.unpackIntValue(key: String): Int = get(key).asIntegerValue().asInt()

  private fun Map<Value, Value>.unpackString(key: String): String =
    get(key).asStringValue().asString()

  private fun Map<Value, Value>.unpackStringOrNull(key: String): String? =
    getNullable(key)?.asStringValue()?.asString()

  private fun Map<Value, Value>.unpackByteArrayOrNull(key: String): ByteArray? =
    getNullable(key)?.asBinaryValue()?.asByteArray()

  private fun Map<Value, Value>.unpackPathElements(key: String): List<PathElement>? =
    getNullable(key)?.asArrayValue()?.map { pathElement ->
      val map = pathElement.asMapValue().map()
      PathElement(map.unpackString("name"), map.unpackBoolean("isDirectory"))
    }

  private fun Map<Value, Value>.unpackModuleReaderSpec(name: String): List<ModuleReaderSpec>? {
    val keys = getNullable(name) ?: return null
    return keys.asArrayValue().toList().map { value ->
      val readerMap = value.asMapValue().map()
      ModuleReaderSpec(
        scheme = readerMap.unpackString("scheme"),
        hasHierarchicalUris = readerMap.unpackBoolean("hasHierarchicalUris"),
        isLocal = readerMap.unpackBoolean("isLocal"),
        isGlobbable = readerMap.unpackBoolean("isGlobbable")
      )
    }
  }

  private fun Map<Value, Value>.unpackResourceReaderSpec(name: String): List<ResourceReaderSpec> {
    val keys = getNullable(name) ?: return emptyList()
    return keys.asArrayValue().toList().map { value ->
      val readerMap = value.asMapValue().map()
      ResourceReaderSpec(
        scheme = readerMap.unpackString("scheme"),
        hasHierarchicalUris = readerMap.unpackBoolean("hasHierarchicalUris"),
        isGlobbable = readerMap.unpackBoolean("isGlobbable")
      )
    }
  }

  private fun Map<Value, Value>.unpackProject(name: String): Project? {
    val projMap = getNullable(name)?.asMapValue()?.map() ?: return null
    val projectFileUri = URI(projMap.unpackString("projectFileUri"))
    val dependencies = projMap.unpackDependencies("dependencies")
    return Project(projectFileUri, null, dependencies)
  }

  private fun Map<Value, Value>.unpackHttp(name: String): PklEvaluatorSettings.Http? {
    val httpMap = getNullable(name)?.asMapValue()?.map() ?: return null
    val proxy = httpMap.unpackProxy("proxy")
    return PklEvaluatorSettings.Http(proxy)
  }

  private fun Map<Value, Value>.unpackProxy(name: String): PklEvaluatorSettings.Proxy? {
    val proxyMap = getNullable(name)?.asMapValue()?.map() ?: return null
    val address = proxyMap.unpackString("address")
    val noProxy = proxyMap.unpackStringListOrNull("noProxy")
    return PklEvaluatorSettings.Proxy.create(address, noProxy)
  }

  private fun Map<Value, Value>.unpackDependencies(name: String): Map<String, Dependency> {
    val mapValue = get(name).asMapValue().map()
    return mapValue.entries.associate { (key, value) ->
      val dependencyName = key.asStringValue().asString()
      val dependencyObj = value.asMapValue().map()
      val type = dependencyObj.unpackString("type")
      val packageUri = URI(dependencyObj.unpackString("packageUri"))
      if (type == DependencyType.REMOTE.value) {
        val checksums =
          dependencyObj.getNullable("checksums")?.asMapValue()?.map()?.let { obj ->
            val sha256 = obj.unpackString("sha256")
            Checksums(sha256)
          }
        return@associate dependencyName to RemoteDependency(packageUri, checksums)
      }
      val dependencies = dependencyObj.unpackDependencies("dependencies")
      val projectFileUri = dependencyObj.unpackString("projectFileUri")
      dependencyName to Project(URI(projectFileUri), packageUri, dependencies)
    }
  }
}
