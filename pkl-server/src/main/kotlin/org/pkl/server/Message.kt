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

import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.util.*
import java.util.regex.Pattern
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings.Proxy
import org.pkl.core.module.PathElement
import org.pkl.core.packages.Checksums

sealed interface Message {
  val type: MessageType
}

sealed interface OneWayMessage : Message

sealed interface RequestMessage : Message {
  val requestId: Long
}

sealed interface ResponseMessage : Message {
  val requestId: Long
}

sealed class ClientMessage : Message

sealed class ClientRequestMessage : ClientMessage(), RequestMessage

sealed class ClientResponseMessage : ClientMessage(), ResponseMessage

sealed class ClientOneWayMessage : ClientMessage(), OneWayMessage

sealed class ServerMessage : Message

sealed class ServerRequestMessage : ServerMessage(), RequestMessage

sealed class ServerResponseMessage : ServerMessage(), ResponseMessage

sealed class ServerOneWayMessage : ServerMessage(), OneWayMessage

enum class MessageType(val code: Int) {
  CREATE_EVALUATOR_REQUEST(0x20),
  CREATE_EVALUATOR_RESPONSE(0x21),
  CLOSE_EVALUATOR(0x22),
  EVALUATE_REQUEST(0x23),
  EVALUATE_RESPONSE(0x24),
  LOG_MESSAGE(0x25),
  READ_RESOURCE_REQUEST(0x26),
  READ_RESOURCE_RESPONSE(0x27),
  READ_MODULE_REQUEST(0x28),
  READ_MODULE_RESPONSE(0x29),
  LIST_RESOURCES_REQUEST(0x2a),
  LIST_RESOURCES_RESPONSE(0x2b),
  LIST_MODULES_REQUEST(0x2c),
  LIST_MODULES_RESPONSE(0x2d),
}

data class ModuleReaderSpec(
  val scheme: String,
  val hasHierarchicalUris: Boolean,
  val isLocal: Boolean,
  val isGlobbable: Boolean
)

data class ResourceReaderSpec(
  val scheme: String,
  val hasHierarchicalUris: Boolean,
  val isGlobbable: Boolean,
)

private fun <T> T?.equalsNullable(other: Any?): Boolean {
  return Objects.equals(this, other)
}

enum class DependencyType(val value: String) {
  LOCAL("local"),
  REMOTE("remote")
}

sealed interface Dependency {
  val type: DependencyType
  val packageUri: URI?
}

data class RemoteDependency(override val packageUri: URI, val checksums: Checksums?) : Dependency {
  override val type: DependencyType = DependencyType.REMOTE
}

data class Project(
  val projectFileUri: URI,
  override val packageUri: URI?,
  val dependencies: Map<String, Dependency>
) : Dependency {
  override val type: DependencyType = DependencyType.LOCAL
}

data class CreateEvaluatorRequest(
  override val requestId: Long,
  val allowedModules: List<Pattern>?,
  val allowedResources: List<Pattern>?,
  val clientModuleReaders: List<ModuleReaderSpec>?,
  val clientResourceReaders: List<ResourceReaderSpec>?,
  val modulePaths: List<Path>?,
  val env: Map<String, String>?,
  val properties: Map<String, String>?,
  val timeout: Duration?,
  val rootDir: Path?,
  val cacheDir: Path?,
  val outputFormat: String?,
  val project: Project?,
  val http: Http?
) : ClientRequestMessage() {
  override val type = MessageType.CREATE_EVALUATOR_REQUEST

  // need to implement this manually because [Pattern.equals] returns false for two patterns
  // that have the same underlying pattern string.
  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (other !is CreateEvaluatorRequest) return false
    return requestId == other.requestId &&
      Objects.equals(
        allowedModules?.map { it.pattern() },
        other.allowedModules?.map { it.pattern() }
      ) &&
      Objects.equals(
        allowedResources?.map { it.pattern() },
        other.allowedResources?.map { it.pattern() }
      ) &&
      clientModuleReaders.equalsNullable(other.clientModuleReaders) &&
      clientResourceReaders.equalsNullable(other.clientResourceReaders) &&
      modulePaths.equalsNullable(other.modulePaths) &&
      env.equalsNullable(other.env) &&
      properties.equalsNullable(other.properties) &&
      timeout.equalsNullable(other.timeout) &&
      rootDir.equalsNullable(other.rootDir) &&
      cacheDir.equalsNullable(other.cacheDir) &&
      outputFormat.equalsNullable(other.outputFormat) &&
      project.equalsNullable(other.project) &&
      http.equalsNullable(other.http)
  }

  @Suppress("DuplicatedCode") // false duplicate within method
  override fun hashCode(): Int {
    var result = requestId.hashCode()
    result = 31 * result + allowedModules?.map { it.pattern() }.hashCode()
    result = 31 * result + allowedResources?.map { it.pattern() }.hashCode()
    result = 31 * result + clientModuleReaders.hashCode()
    result = 31 * result + clientResourceReaders.hashCode()
    result = 31 * result + modulePaths.hashCode()
    result = 31 * result + env.hashCode()
    result = 31 * result + properties.hashCode()
    result = 31 * result + timeout.hashCode()
    result = 31 * result + rootDir.hashCode()
    result = 31 * result + cacheDir.hashCode()
    result = 31 * result + outputFormat.hashCode()
    result = 31 * result + project.hashCode()
    result = 31 * result + type.hashCode()
    result = 31 * result + http.hashCode()
    return result
  }
}

data class Http(
  /** PEM-format CA certificates as raw bytes. */
  val caCertificates: ByteArray?,
  /** Proxy settings */
  val proxy: Proxy?
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Http) return false

    if (caCertificates != null) {
      if (other.caCertificates == null) return false
      if (!caCertificates.contentEquals(other.caCertificates)) return false
    } else if (other.caCertificates != null) return false
    return Objects.equals(proxy, other.proxy)
  }

  override fun hashCode(): Int {
    var result = caCertificates?.contentHashCode() ?: 0
    result = 31 * result + (proxy?.hashCode() ?: 0)
    return result
  }
}

data class CreateEvaluatorResponse(
  override val requestId: Long,
  val evaluatorId: Long?,
  val error: String?,
) : ServerResponseMessage() {
  override val type
    get() = MessageType.CREATE_EVALUATOR_RESPONSE
}

data class ListResourcesRequest(override val requestId: Long, val evaluatorId: Long, val uri: URI) :
  ServerRequestMessage() {
  override val type: MessageType
    get() = MessageType.LIST_RESOURCES_REQUEST
}

data class ListResourcesResponse(
  override val requestId: Long,
  val evaluatorId: Long,
  val pathElements: List<PathElement>?,
  val error: String?
) : ClientResponseMessage() {
  override val type: MessageType
    get() = MessageType.LIST_RESOURCES_RESPONSE
}

data class ListModulesRequest(override val requestId: Long, val evaluatorId: Long, val uri: URI) :
  ServerRequestMessage() {
  override val type: MessageType
    get() = MessageType.LIST_MODULES_REQUEST
}

data class ListModulesResponse(
  override val requestId: Long,
  val evaluatorId: Long,
  val pathElements: List<PathElement>?,
  val error: String?
) : ClientResponseMessage() {
  override val type: MessageType
    get() = MessageType.LIST_MODULES_RESPONSE
}

data class CloseEvaluator(val evaluatorId: Long) : ClientOneWayMessage() {
  override val type = MessageType.CLOSE_EVALUATOR
}

data class EvaluateRequest(
  override val requestId: Long,
  val evaluatorId: Long,
  val moduleUri: URI,
  val moduleText: String?,
  val expr: String?
) : ClientRequestMessage() {
  override val type = MessageType.EVALUATE_REQUEST
}

data class EvaluateResponse(
  override val requestId: Long,
  val evaluatorId: Long,
  val result: ByteArray?,
  val error: String?
) : ServerResponseMessage() {
  override val type
    get() = MessageType.EVALUATE_RESPONSE

  // override to use [ByteArray.contentEquals]
  @Suppress("DuplicatedCode")
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is EvaluateResponse) return false

    return requestId == other.requestId &&
      evaluatorId == other.evaluatorId &&
      result.contentEquals(other.result) &&
      error == other.error
  }

  // override to use [ByteArray.contentHashCode]
  override fun hashCode(): Int {
    var result1 = requestId.hashCode()
    result1 = 31 * result1 + evaluatorId.hashCode()
    result1 = 31 * result1 + result.contentHashCode()
    result1 = 31 * result1 + error.hashCode()
    return result1
  }
}

data class LogMessage(
  val evaluatorId: Long,
  val level: Int,
  val message: String,
  val frameUri: String
) : ServerOneWayMessage() {
  override val type
    get() = MessageType.LOG_MESSAGE
}

data class ReadResourceRequest(override val requestId: Long, val evaluatorId: Long, val uri: URI) :
  ServerRequestMessage() {
  override val type
    get() = MessageType.READ_RESOURCE_REQUEST
}

data class ReadResourceResponse(
  override val requestId: Long,
  val evaluatorId: Long,
  val contents: ByteArray?,
  val error: String?
) : ClientResponseMessage() {
  override val type = MessageType.READ_RESOURCE_RESPONSE

  // override to use [ByteArray.contentEquals]
  @Suppress("DuplicatedCode")
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ReadResourceResponse) return false

    return requestId == other.requestId &&
      evaluatorId == other.evaluatorId &&
      contents.contentEquals(other.contents) &&
      error == other.error
  }

  // override to use [ByteArray.contentHashCode]
  override fun hashCode(): Int {
    var result = requestId.hashCode()
    result = 31 * result + evaluatorId.hashCode()
    result = 31 * result + contents.contentHashCode()
    result = 31 * result + error.hashCode()
    return result
  }
}

data class ReadModuleRequest(override val requestId: Long, val evaluatorId: Long, val uri: URI) :
  ServerRequestMessage() {
  override val type
    get() = MessageType.READ_MODULE_REQUEST
}

data class ReadModuleResponse(
  override val requestId: Long,
  val evaluatorId: Long,
  val contents: String?,
  val error: String?
) : ClientResponseMessage() {
  override val type = MessageType.READ_MODULE_RESPONSE
}
