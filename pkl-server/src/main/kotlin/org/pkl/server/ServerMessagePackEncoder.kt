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

import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.pathString
import org.msgpack.core.MessagePack
import org.msgpack.core.MessagePacker
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings.ExternalReader
import org.pkl.core.messaging.BaseMessagePackEncoder
import org.pkl.core.messaging.Message
import org.pkl.core.packages.Checksums

class ServerMessagePackEncoder(packer: MessagePacker) : BaseMessagePackEncoder(packer) {

  constructor(stream: OutputStream) : this(MessagePack.newDefaultPacker(stream))

  private fun MessagePacker.packProject(project: Project) {
    packMapHeader(2)
    packKeyValue("projectFileUri", project.projectFileUri.toString())
    packString("dependencies")
    packDependencies(project.dependencies)
  }

  private fun MessagePacker.packHttp(http: Http) {
    packMapHeader(0, http.caCertificates, http.proxy)
    packKeyValue("caCertificates", http.caCertificates)
    http.proxy?.let { proxy ->
      packString("proxy")
      packMapHeader(0, proxy.address, proxy.noProxy)
      packKeyValue("address", proxy.address?.toString())
      packKeyValue("noProxy", proxy.noProxy)
    }
  }

  private fun MessagePacker.packDependencies(dependencies: Map<String, Dependency>) {
    packMapHeader(dependencies.size)
    for ((name, dep) in dependencies) {
      packString(name)
      if (dep is Project) {
        packMapHeader(4)
        packKeyValue("type", dep.type.value)
        packKeyValue("packageUri", dep.packageUri.toString())
        packKeyValue("projectFileUri", dep.projectFileUri.toString())
        packString("dependencies")
        packDependencies(dep.dependencies)
      } else {
        dep as RemoteDependency
        packMapHeader(dep.checksums?.let { 3 } ?: 2)
        packKeyValue("type", dep.type.value)
        packKeyValue("packageUri", dep.packageUri.toString())
        dep.checksums?.let { checksums ->
          packString("checksums")
          packChecksums(checksums)
        }
      }
    }
  }

  private fun MessagePacker.packChecksums(checksums: Checksums) {
    packMapHeader(1)
    packKeyValue("sha256", checksums.sha256)
  }

  private fun MessagePacker.packExternalReader(spec: ExternalReader) {
    packMapHeader(1, spec.arguments)
    packKeyValue("executable", spec.executable)
    spec.arguments?.let { packKeyValue("arguments", it) }
  }

  override fun encodeMessage(msg: Message) {
    when (msg.type) {
      Message.Type.CREATE_EVALUATOR_REQUEST -> {
        msg as CreateEvaluatorRequest
        packMapHeader(
          1,
          msg.allowedModules,
          msg.allowedResources,
          msg.clientModuleReaders,
          msg.clientResourceReaders,
          msg.modulePaths,
          msg.env,
          msg.properties,
          msg.timeout,
          msg.rootDir,
          msg.cacheDir,
          msg.outputFormat,
          msg.project,
          msg.http,
          msg.externalModuleReaders,
          msg.externalResourceReaders,
        )
        packKeyValue("requestId", msg.requestId)
        packKeyValue("allowedModules", msg.allowedModules?.map { it.toString() })
        packKeyValue("allowedResources", msg.allowedResources?.map { it.toString() })
        if (msg.clientModuleReaders != null) {
          packer.packString("clientModuleReaders")
          packer.packArrayHeader(msg.clientModuleReaders.size)
          for (moduleReader in msg.clientModuleReaders) {
            packModuleReaderSpec(moduleReader)
          }
        }
        if (msg.clientResourceReaders != null) {
          packer.packString("clientResourceReaders")
          packer.packArrayHeader(msg.clientResourceReaders.size)
          for (resourceReader in msg.clientResourceReaders) {
            packResourceReaderSpec(resourceReader)
          }
        }
        packKeyValue("modulePaths", msg.modulePaths, Path::toString)
        packKeyValue("env", msg.env)
        packKeyValue("properties", msg.properties)
        packKeyValue("timeoutSeconds", msg.timeout?.toSeconds())
        packKeyValue("rootDir", msg.rootDir?.pathString)
        packKeyValue("cacheDir", msg.cacheDir?.pathString)
        packKeyValue("outputFormat", msg.outputFormat)
        if (msg.project != null) {
          packer.packString("project")
          packer.packProject(msg.project)
        }
        if (msg.http != null) {
          packer.packString("http")
          packer.packHttp(msg.http)
        }
        if (msg.externalModuleReaders != null) {
          packer.packString("externalModuleReaders")
          packer.packMapHeader(msg.externalModuleReaders.size)
          for ((scheme, spec) in msg.externalModuleReaders) {
            packer.packString(scheme)
            packer.packExternalReader(spec)
          }
        }
        if (msg.externalResourceReaders != null) {
          packer.packString("externalResourceReaders")
          packer.packMapHeader(msg.externalResourceReaders.size)
          for ((scheme, spec) in msg.externalResourceReaders) {
            packer.packString(scheme)
            packer.packExternalReader(spec)
          }
        }
        return
      }
      Message.Type.CREATE_EVALUATOR_RESPONSE -> {
        msg as CreateEvaluatorResponse
        packMapHeader(1, msg.evaluatorId, msg.error)
        packKeyValue("requestId", msg.requestId)
        packKeyValue("evaluatorId", msg.evaluatorId)
        packKeyValue("error", msg.error)
      }
      Message.Type.CLOSE_EVALUATOR -> {
        msg as CloseEvaluator
        packer.packMapHeader(1)
        packKeyValue("evaluatorId", msg.evaluatorId)
      }
      Message.Type.EVALUATE_REQUEST -> {
        msg as EvaluateRequest
        packMapHeader(3, msg.moduleText, msg.expr)
        packKeyValue("requestId", msg.requestId)
        packKeyValue("evaluatorId", msg.evaluatorId)
        packKeyValue("moduleUri", msg.moduleUri.toString())
        packKeyValue("moduleText", msg.moduleText)
        packKeyValue("expr", msg.expr)
      }
      Message.Type.EVALUATE_RESPONSE -> {
        msg as EvaluateResponse
        packMapHeader(2, msg.result, msg.error)
        packKeyValue("requestId", msg.requestId)
        packKeyValue("evaluatorId", msg.evaluatorId)
        packKeyValue("result", msg.result)
        packKeyValue("error", msg.error)
      }
      Message.Type.LOG_MESSAGE -> {
        msg as LogMessage
        packer.packMapHeader(4)
        packKeyValue("evaluatorId", msg.evaluatorId)
        packKeyValue("level", msg.level)
        packKeyValue("message", msg.message)
        packKeyValue("frameUri", msg.frameUri)
      }
      else -> super.encodeMessage(msg)
    }
  }
}
