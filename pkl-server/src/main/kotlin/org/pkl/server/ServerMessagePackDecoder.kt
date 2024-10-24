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

import java.io.InputStream
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.util.regex.Pattern
import org.msgpack.core.MessagePack
import org.msgpack.core.MessageUnpacker
import org.msgpack.value.Value
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings.ExternalReader
import org.pkl.core.messaging.BaseMessagePackDecoder
import org.pkl.core.messaging.Message
import org.pkl.core.messaging.Messages.Bytes
import org.pkl.core.packages.Checksums

class ServerMessagePackDecoder(unpacker: MessageUnpacker) : BaseMessagePackDecoder(unpacker) {

  constructor(stream: InputStream) : this(MessagePack.newDefaultUnpacker(stream))

  override fun decodeMessage(msgType: Message.Type, map: Map<Value, Value>): Message? {
    return when (msgType) {
      Message.Type.CREATE_EVALUATOR_REQUEST ->
        CreateEvaluatorRequest(
          get(map, "requestId").asIntegerValue().asLong(),
          unpackStringListOrNull(map, "allowedModules", Pattern::compile),
          unpackStringListOrNull(map, "allowedResources", Pattern::compile),
          unpackListOrNull(map, "clientModuleReaders") { unpackModuleReaderSpec(it)!! },
          unpackListOrNull(map, "clientResourceReaders") { unpackResourceReaderSpec(it)!! },
          unpackStringListOrNull(map, "modulePaths", Path::of),
          unpackStringMapOrNull(map, "env"),
          unpackStringMapOrNull(map, "properties"),
          unpackLongOrNull(map, "timeoutSeconds", Duration::ofSeconds),
          unpackStringOrNull(map, "rootDir", Path::of),
          unpackStringOrNull(map, "cacheDir", Path::of),
          unpackStringOrNull(map, "outputFormat"),
          map.unpackProject(),
          map.unpackHttp(),
          unpackStringMapOrNull(map, "externalModuleReaders", ::unpackExternalReader),
          unpackStringMapOrNull(map, "externalResourceReaders", ::unpackExternalReader)
        )
      Message.Type.CREATE_EVALUATOR_RESPONSE ->
        CreateEvaluatorResponse(
          unpackLong(map, "requestId"),
          unpackLongOrNull(map, "evaluatorId"),
          unpackStringOrNull(map, "error")
        )
      Message.Type.CLOSE_EVALUATOR -> CloseEvaluator(unpackLong(map, "evaluatorId"))
      Message.Type.EVALUATE_REQUEST ->
        EvaluateRequest(
          unpackLong(map, "requestId"),
          unpackLong(map, "evaluatorId"),
          URI(unpackString(map, "moduleUri")),
          unpackStringOrNull(map, "moduleText"),
          unpackStringOrNull(map, "expr")
        )
      Message.Type.EVALUATE_RESPONSE ->
        EvaluateResponse(
          unpackLong(map, "requestId"),
          unpackLong(map, "evaluatorId"),
          unpackByteArray(map, "result"),
          unpackStringOrNull(map, "error")
        )
      Message.Type.LOG_MESSAGE ->
        LogMessage(
          unpackLong(map, "evaluatorId"),
          unpackInt(map, "level"),
          unpackString(map, "message"),
          unpackString(map, "frameUri")
        )
      else -> super.decodeMessage(msgType, map)
    }
  }

  private fun Map<Value, Value>.unpackProject(): Project? {
    val projMap = getNullable(this, "project")?.asMapValue()?.map() ?: return null
    val projectFileUri = URI(unpackString(projMap, "projectFileUri"))
    val dependencies = projMap.unpackDependencies("dependencies")
    return Project(projectFileUri, null, dependencies)
  }

  private fun Map<Value, Value>.unpackHttp(): Http? {
    val httpMap = getNullable(this, "http")?.asMapValue()?.map() ?: return null
    val proxy = httpMap.unpackProxy()
    val caCertificates =
      getNullable(httpMap, "caCertificates")?.asBinaryValue()?.asByteArray()?.let(::Bytes)
    return Http(caCertificates, proxy)
  }

  private fun Map<Value, Value>.unpackProxy(): PklEvaluatorSettings.Proxy? {
    val proxyMap = getNullable(this, "proxy")?.asMapValue()?.map() ?: return null
    val address = unpackString(proxyMap, "address")
    val noProxy = unpackStringListOrNull(proxyMap, "noProxy")
    return PklEvaluatorSettings.Proxy.create(address, noProxy)
  }

  private fun Map<Value, Value>.unpackDependencies(name: String): Map<String, Dependency> {
    val mapValue = get(this, name).asMapValue().map()
    return mapValue.entries.associate { (key, value) ->
      val dependencyName = key.asStringValue().asString()
      val dependencyObj = value.asMapValue().map()
      val type = unpackString(dependencyObj, "type")
      val packageUri = URI(unpackString(dependencyObj, "packageUri"))
      if (type == DependencyType.REMOTE.value) {
        val checksums =
          getNullable(dependencyObj, "checksums")?.asMapValue()?.map()?.let { obj ->
            val sha256 = unpackString(obj, "sha256")
            Checksums(sha256)
          }
        return@associate dependencyName to RemoteDependency(packageUri, checksums)
      }
      val dependencies = dependencyObj.unpackDependencies("dependencies")
      val projectFileUri = unpackString(dependencyObj, "projectFileUri")
      dependencyName to Project(URI(projectFileUri), packageUri, dependencies)
    }
  }

  private fun unpackExternalReader(map: Map<Value, Value>): ExternalReader =
    ExternalReader(unpackString(map, "executable"), unpackStringListOrNull(map, "arguments")!!)
}
