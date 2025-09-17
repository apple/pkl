/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.commons.test

import java.util.*
import org.msgpack.core.MessagePack
import org.msgpack.core.MessageUnpacker
import org.msgpack.value.ValueType

/** Renders MessagePack structures in YAML. */
class MessagePackDebugRenderer(bytes: ByteArray) {
  private val unpacker: MessageUnpacker = MessagePack.newDefaultUnpacker(bytes)
  private val currIndent = StringBuilder("")
  private val sb = StringBuilder()
  private val indent = "  "

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
      ValueType.STRING -> emitString(unpacker.unpackString())
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
      ValueType.STRING -> emitString(unpacker.unpackString())
      ValueType.ARRAY -> {
        val size = unpacker.unpackArrayHeader()
        if (size == 0) {
          sb.append("[]")
          return
        }
        repeat(size) {
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
        repeat(size) {
          newline()
          renderKey()
          incIndent()
          renderValue()
          decIndent()
        }
      }
      ValueType.BINARY -> {
        // https://yaml.org/type/binary.html
        sb.append("!!binary ")
        val size = unpacker.unpackBinaryHeader()
        emitString(Base64.getEncoder().encodeToString(unpacker.readPayload(size)))
      }
      ValueType.EXTENSION -> throw IllegalStateException("Unexpected value type ${mf.valueType}")
    }
  }

  val output: String by lazy {
    renderValue()
    sb.toString().removePrefix("\n")
  }

  fun emitString(str: String) {
    val newlineIndex = str.indexOf('\n')
    if (newlineIndex < 0) {
      emitSingleLineString(str)
    } else {
      emitMultiLineString(str, newlineIndex)
    }
  }

  // adapted from org.pkl.core.util.yaml.YamlEmitter.emitSingleQuotedString
  fun emitSingleLineString(str: String) {
    sb.append('\'')

    val singleQuoteIndex = str.indexOfFirst { it == '\'' }
    if (singleQuoteIndex == -1) {
      sb.append(str)
    } else {
      var start = 0
      val length = str.length
      for (i in singleQuoteIndex..<length) {
        if (str[i] == '\'') {
          sb.append(str, start, i).append("''")
          start = i + 1
        }
      }
      if (start < length) {
        sb.append(str, start, length)
      }
    }

    sb.append('\'')
  }

  // adapted from org.pkl.core.util.yaml.YamlEmitter.emitSingleQuotedString
  fun emitMultiLineString(str: String, newlineIndex: Int) {
    currIndent.append(indent)

    sb.append('|')
    if (str.first() == ' ') {
      sb.append(indent.length)
    }

    val length = str.length
    if (str.last() == '\n') {
      if (length == 1 || str[length - 2] == '\n') {
        sb.append('+')
      }
    } else {
      sb.append('-')
    }

    sb.append('\n')

    var start = 0
    for (i in newlineIndex..<length) {
      if (str[i] != '\n') continue
      if (i == start) {
        // don't add leading indent before newline
        sb.append('\n')
      } else {
        sb.append(currIndent).append(str, start, i + 1)
      }
      start = i + 1
    }
    if (start < length) {
      sb.append(currIndent).append(str, start, length)
    }

    currIndent.setLength(currIndent.length - indent.length)
  }
}
