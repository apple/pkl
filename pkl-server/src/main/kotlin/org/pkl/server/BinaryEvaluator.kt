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

import java.nio.file.Path
import java.time.Duration
import org.msgpack.core.MessagePacker
import org.pkl.core.*
import org.pkl.core.ast.member.ObjectMember
import org.pkl.core.module.ModuleKeyFactory
import org.pkl.core.project.DeclaredDependencies
import org.pkl.core.resource.ResourceReader
import org.pkl.core.runtime.*

internal class BinaryEvaluator(
  transformer: StackFrameTransformer,
  manager: SecurityManager,
  logger: Logger,
  factories: Collection<ModuleKeyFactory?>,
  readers: Collection<ResourceReader?>,
  environmentVariables: Map<String, String>,
  externalProperties: Map<String, String>,
  timeout: Duration?,
  moduleCacheDir: Path?,
  declaredDependencies: DeclaredDependencies?,
  outputFormat: String?
) :
  EvaluatorImpl(
    transformer,
    manager,
    logger,
    factories,
    readers,
    environmentVariables,
    externalProperties,
    timeout,
    moduleCacheDir,
    declaredDependencies,
    outputFormat
  ) {
  fun evaluate(moduleSource: ModuleSource, expression: String?): ByteArray {
    return doEvaluate(moduleSource) { module ->
      val evalResult =
        expression?.let { VmUtils.evaluateExpression(module, it, securityManager, moduleResolver) }
          ?: module
      VmValue.force(evalResult, false)
      threadLocalBufferPacker
        .get()
        .apply {
          clear()
          ValueEncoder(this).visit(evalResult)
        }
        .toByteArray()
    }
  }

  private class ValueEncoder(private val packer: MessagePacker) : VmValueVisitor {
    companion object {
      private const val CODE_OBJECT: Byte = 0x1
      private const val CODE_MAP: Byte = 0x2
      private const val CODE_MAPPING: Byte = 0x3
      private const val CODE_LIST: Byte = 0x4
      private const val CODE_LISTING: Byte = 0x5
      private const val CODE_SET: Byte = 0x6
      private const val CODE_DURATION: Byte = 0x7
      private const val CODE_DATASIZE: Byte = 0x8
      private const val CODE_PAIR: Byte = 0x9
      private const val CODE_INTSEQ: Byte = 0xA
      private const val CODE_REGEX: Byte = 0xB
      private const val CODE_CLASS: Byte = 0xC
      private const val CODE_TYPEALIAS: Byte = 0xD
      private const val CODE_FUNCTION: Byte = 0xE
      private const val CODE_PROPERTY: Byte = 0x10
      private const val CODE_ENTRY: Byte = 0x11
      private const val CODE_ELEMENT: Byte = 0x12
    }

    override fun visitString(value: String) {
      packer.packString(value)
    }

    override fun visitBoolean(value: Boolean) {
      packer.packBoolean(value)
    }

    override fun visitInt(value: Long) {
      packer.packLong(value)
    }

    override fun visitFloat(value: Double) {
      packer.packDouble(value)
    }

    override fun visitDuration(value: VmDuration) {
      packer.packArrayHeader(3)
      packer.packInt(CODE_DURATION.toInt())
      packer.packDouble(value.value)
      packer.packString(value.unit.toString())
    }

    override fun visitDataSize(value: VmDataSize) {
      packer.packArrayHeader(3)
      packer.packInt(CODE_DATASIZE.toInt())
      packer.packDouble(value.value)
      packer.packString(value.unit.toString())
    }

    override fun visitIntSeq(value: VmIntSeq) {
      packer.packArrayHeader(4)
      packer.packInt(CODE_INTSEQ.toInt())
      packer.packLong(value.start)
      packer.packLong(value.end)
      packer.packLong(value.step)
    }

    private fun doVisitCollection(length: Int, value: Iterable<Any>) {
      packer.packArrayHeader(length)
      for (elem in value) {
        visit(elem)
      }
    }

    override fun visitList(value: VmList) {
      packer.packArrayHeader(2)
      packer.packInt(CODE_LIST.toInt())
      doVisitCollection(value.length, value)
    }

    override fun visitSet(value: VmSet) {
      packer.packArrayHeader(2)
      packer.packInt(CODE_SET.toInt())
      doVisitCollection(value.length, value)
    }

    override fun visitMap(value: VmMap) {
      packer.packArrayHeader(2)
      packer.packInt(CODE_MAP.toInt())
      packer.packMapHeader(value.length)
      for ((k, v) in value) {
        visit(k)
        visit(v)
      }
    }

    override fun visitTyped(value: VmTyped) {
      packObjectPreamble(value)
      packer.packArrayHeader(value.vmClass.allRegularPropertyNames.size())
      value.iterateAlreadyForcedMemberValues(this::doVisitObjectMember)
    }

    private fun doVisitObjectMember(key: Any, member: ObjectMember, value: Any): Boolean {
      if (member.isClass || member.isTypeAlias) return true

      packer.packArrayHeader(3)
      when {
        member.isProp -> {
          packer.packInt(CODE_PROPERTY.toInt())
          packer.packString(key.toString())
        }
        member.isEntry -> {
          packer.packInt(CODE_ENTRY.toInt())
          visit(key)
        }
        else -> {
          packer.packInt(CODE_ELEMENT.toInt())
          packer.packLong(key as Long)
        }
      }
      visit(value)
      return true
    }

    override fun visitDynamic(value: VmDynamic) {
      packObjectPreamble(value)
      packer.packArrayHeader(value.regularMemberCount)
      value.iterateAlreadyForcedMemberValues(this::doVisitObjectMember)
    }

    private fun packObjectPreamble(value: VmObjectLike) {
      packer.packArrayHeader(4)
      packer.packInt(CODE_OBJECT.toInt())
      packer.packString(value.vmClass.displayName)
      packer.packString(value.vmClass.module.moduleInfo.moduleKey.uri.toString())
    }

    override fun visitListing(value: VmListing) {
      packer.packArrayHeader(2)
      packer.packInt(CODE_LISTING.toInt())
      packer.packArrayHeader(value.length)
      value.iterateAlreadyForcedMemberValues { _, _, memberValue ->
        visit(memberValue)
        true
      }
    }

    override fun visitMapping(value: VmMapping) {
      packer.packArrayHeader(2)
      packer.packInt(CODE_MAPPING.toInt())
      packer.packMapHeader(value.entryCount)
      value.iterateAlreadyForcedMemberValues { key, _, memberValue ->
        visit(key)
        visit(memberValue)
        true
      }
    }

    override fun visitClass(value: VmClass) {
      packer.packArrayHeader(1)
      packer.packInt(CODE_CLASS.toInt())
    }

    override fun visitTypeAlias(value: VmTypeAlias) {
      packer.packArrayHeader(1)
      packer.packInt(CODE_TYPEALIAS.toInt())
    }

    override fun visitPair(value: VmPair) {
      packer.packArrayHeader(3)
      packer.packInt(CODE_PAIR.toInt())
      visit(value.first)
      visit(value.second)
    }

    override fun visitRegex(value: VmRegex) {
      packer.packArrayHeader(2)
      packer.packInt(CODE_REGEX.toInt())
      packer.packString(value.pattern.pattern())
    }

    override fun visitNull(value: VmNull) {
      packer.packNil()
    }

    override fun visitFunction(value: VmFunction) {
      packer.packArrayHeader(1)
      packer.packInt(CODE_FUNCTION.toInt())
    }
  }
}
