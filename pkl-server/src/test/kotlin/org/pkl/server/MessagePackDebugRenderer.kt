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

import java.lang.IllegalStateException
import org.msgpack.core.MessagePack
import org.msgpack.core.MessageUnpacker
import org.msgpack.value.ValueType
import org.pkl.core.util.yaml.YamlEmitter

/** Renders MessagePack structures in YAML. */
class MessagePackDebugRenderer(bytes: ByteArray) {
  private val unpacker: MessageUnpacker = MessagePack.newDefaultUnpacker(bytes)
  private val currIndent = StringBuilder("")
  private val sb = StringBuilder()
  private val indent = "  "
  private val yamlEmitter = YamlEmitter.create(sb, "1.2", indent)

  private fun incIndent() {
    currIndent.append(indent)
  }

  private fun decIndent() {
    currIndent.setLength(currIndent.length - indent.length)
  }

  private fun newline() {
    sb.append("\n")
    sb.append(currIndent)
  }

  private fun renderKey() {
    val mf = unpacker.nextFormat
    when (mf.valueType!!) {
      ValueType.STRING -> yamlEmitter.emit(unpacker.unpackString(), currIndent, true)
      ValueType.MAP,
      ValueType.ARRAY -> {
        sb.append("? ")
        incIndent()
        renderValue()
        decIndent()
        newline()
      }
      else -> renderValue()
    }
    sb.append(": ")
  }

  private fun renderValue() {
    val mf = unpacker.nextFormat
    when (mf.valueType!!) {
      ValueType.INTEGER,
      ValueType.FLOAT,
      ValueType.BOOLEAN,
      ValueType.NIL -> sb.append(unpacker.unpackValue().toJson())
      ValueType.STRING -> yamlEmitter.emit(unpacker.unpackString(), currIndent, false)
      ValueType.ARRAY -> {
        val size = unpacker.unpackArrayHeader()
        if (size == 0) {
          sb.append("[]")
          return
        }
        for (i in 0 until size) {
          newline()
          sb.append("- ")
          incIndent()
          renderValue()
          decIndent()
        }
      }
      ValueType.MAP -> {
        val size = unpacker.unpackMapHeader()
        if (size == 0) {
          sb.append("{}")
          return
        }
        for (i in 0 until size) {
          newline()
          renderKey()
          incIndent()
          renderValue()
          decIndent()
        }
      }
      ValueType.BINARY,
      ValueType.EXTENSION -> throw IllegalStateException("Unexpected value type ${mf.valueType}")
    }
  }

  val output by lazy {
    renderValue()
    sb.toString().removePrefix("\n")
  }
}

val ByteArray.debugRendering
  get() = MessagePackDebugRenderer(this).output
