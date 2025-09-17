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

import java.nio.file.Path
import java.time.Duration
import org.pkl.core.*
import org.pkl.core.evaluatorSettings.TraceMode
import org.pkl.core.http.HttpClient
import org.pkl.core.module.ModuleKeyFactory
import org.pkl.core.project.DeclaredDependencies
import org.pkl.core.resource.ResourceReader
import org.pkl.core.runtime.*
import org.pkl.core.runtime.VmPklBinaryEncoder

internal class BinaryEvaluator(
  transformer: StackFrameTransformer,
  manager: SecurityManager,
  httpClient: HttpClient,
  logger: Logger,
  factories: Collection<ModuleKeyFactory?>,
  readers: Collection<ResourceReader?>,
  environmentVariables: Map<String, String>,
  externalProperties: Map<String, String>,
  timeout: Duration?,
  moduleCacheDir: Path?,
  declaredDependencies: DeclaredDependencies?,
  outputFormat: String?,
  traceMode: TraceMode,
) :
  EvaluatorImpl(
    transformer,
    false,
    manager,
    httpClient,
    logger,
    factories,
    readers,
    environmentVariables,
    externalProperties,
    timeout,
    moduleCacheDir,
    declaredDependencies,
    outputFormat,
    traceMode,
  ) {
  fun evaluate(moduleSource: ModuleSource, expression: String?): ByteArray {
    return doEvaluate(moduleSource) { module ->
      val evalResult =
        expression?.let { VmUtils.evaluateExpression(module, it, securityManager, moduleResolver) }
          ?: module
      threadLocalBufferPacker
        .get()
        .apply {
          clear()
          VmPklBinaryEncoder(this).renderDocument(evalResult)
        }
        .toByteArray()
    }
  }
}
