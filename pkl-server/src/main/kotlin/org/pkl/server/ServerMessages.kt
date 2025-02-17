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

import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.util.*
import java.util.regex.Pattern
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings.ExternalReader
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings.Proxy
import org.pkl.core.messaging.Message
import org.pkl.core.messaging.Messages.*
import org.pkl.core.packages.Checksums

private fun <T> T?.equalsNullable(other: Any?): Boolean {
  return Objects.equals(this, other)
}

data class CreateEvaluatorRequest(
  private val requestId: Long,
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
  val http: Http?,
  val externalModuleReaders: Map<String, ExternalReader>?,
  val externalResourceReaders: Map<String, ExternalReader>?,
) : Message.Client.Request {

  override fun type(): Message.Type = Message.Type.CREATE_EVALUATOR_REQUEST

  override fun requestId(): Long = requestId

  // need to implement this manually because [Pattern.equals] returns false for two patterns
  // that have the same underlying pattern string.
  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (other !is CreateEvaluatorRequest) return false
    return requestId == other.requestId &&
      Objects.equals(
        allowedModules?.map { it.pattern() },
        other.allowedModules?.map { it.pattern() },
      ) &&
      Objects.equals(
        allowedResources?.map { it.pattern() },
        other.allowedResources?.map { it.pattern() },
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
      http.equalsNullable(other.http) &&
      externalModuleReaders.equalsNullable(other.externalModuleReaders) &&
      externalResourceReaders.equalsNullable(other.externalResourceReaders)
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
    result = 31 * result + http.hashCode()
    result = 31 * result + externalModuleReaders.hashCode()
    result = 31 * result + externalResourceReaders.hashCode()
    return result
  }
}

data class Http(
  /** PEM-format CA certificates as raw bytes. */
  val caCertificates: ByteArray?,
  /** Proxy settings */
  val proxy: Proxy?,
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

enum class DependencyType(val value: String) {
  LOCAL("local"),
  REMOTE("remote"),
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
  val dependencies: Map<String, Dependency>,
) : Dependency {
  override val type: DependencyType = DependencyType.LOCAL
}

data class CreateEvaluatorResponse(
  private val requestId: Long,
  val evaluatorId: Long?,
  val error: String?,
) : Message.Server.Response {
  override fun type(): Message.Type = Message.Type.CREATE_EVALUATOR_RESPONSE

  override fun requestId(): Long = requestId
}

data class CloseEvaluator(val evaluatorId: Long) : Message.Client.OneWay {
  override fun type(): Message.Type = Message.Type.CLOSE_EVALUATOR
}

data class EvaluateRequest(
  private val requestId: Long,
  val evaluatorId: Long,
  val moduleUri: URI,
  val moduleText: String?,
  val expr: String?,
) : Message.Client.Request {
  override fun type(): Message.Type = Message.Type.EVALUATE_REQUEST

  override fun requestId(): Long = requestId
}

data class EvaluateResponse(
  private val requestId: Long,
  val evaluatorId: Long,
  val result: ByteArray?,
  val error: String?,
) : Message.Server.Response {
  override fun type(): Message.Type = Message.Type.EVALUATE_RESPONSE

  override fun requestId(): Long = requestId

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
  val frameUri: String,
) : Message.Server.OneWay {
  override fun type(): Message.Type = Message.Type.LOG_MESSAGE
}
