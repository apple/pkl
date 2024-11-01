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
package org.pkl.core.util.msgpack.value.impl;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import org.pkl.core.util.msgpack.core.MessagePack;
import org.pkl.core.util.msgpack.core.MessageStringCodingException;
import org.pkl.core.util.msgpack.value.ImmutableRawValue;

public abstract class AbstractImmutableRawValue extends AbstractImmutableValue
    implements ImmutableRawValue {
  protected final byte[] data;
  private volatile String decodedStringCache;
  private volatile CharacterCodingException codingException;

  public AbstractImmutableRawValue(byte[] data) {
    this.data = data;
  }

  public AbstractImmutableRawValue(String string) {
    this.decodedStringCache = string;
    this.data = string.getBytes(MessagePack.UTF8); // TODO
  }

  @Override
  public ImmutableRawValue asRawValue() {
    return this;
  }

  @Override
  public byte[] asByteArray() {
    return Arrays.copyOf(data, data.length);
  }

  @Override
  public ByteBuffer asByteBuffer() {
    return ByteBuffer.wrap(data).asReadOnlyBuffer();
  }

  @Override
  public String asString() {
    if (decodedStringCache == null) {
      decodeString();
    }
    if (codingException != null) {
      throw new MessageStringCodingException(codingException);
    } else {
      return decodedStringCache;
    }
  }

  @Override
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    appendJsonString(sb, toString());
    return sb.toString();
  }

  private void decodeString() {
    synchronized (data) {
      if (decodedStringCache != null) {
        return;
      }
      try {
        CharsetDecoder reportDecoder =
            MessagePack.UTF8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        this.decodedStringCache = reportDecoder.decode(asByteBuffer()).toString();
      } catch (CharacterCodingException ex) {
        try {
          CharsetDecoder replaceDecoder =
              MessagePack.UTF8
                  .newDecoder()
                  .onMalformedInput(CodingErrorAction.REPLACE)
                  .onUnmappableCharacter(CodingErrorAction.REPLACE);
          this.decodedStringCache = replaceDecoder.decode(asByteBuffer()).toString();
        } catch (CharacterCodingException neverThrown) {
          throw new MessageStringCodingException(neverThrown);
        }
        this.codingException = ex;
      }
    }
  }

  @Override
  public String toString() {
    if (decodedStringCache == null) {
      decodeString();
    }
    return decodedStringCache;
  }

  static void appendJsonString(StringBuilder sb, String string) {
    sb.append("\"");
    for (int i = 0; i < string.length(); i++) {
      char ch = string.charAt(i);
      if (ch < 0x20) {
        switch (ch) {
          case '\n':
            sb.append("\\n");
            break;
          case '\r':
            sb.append("\\r");
            break;
          case '\t':
            sb.append("\\t");
            break;
          case '\f':
            sb.append("\\f");
            break;
          case '\b':
            sb.append("\\b");
            break;
          default:
            // control chars
            escapeChar(sb, ch);
            break;
        }
      } else if (ch <= 0x7f) {
        switch (ch) {
          case '\\':
            sb.append("\\\\");
            break;
          case '"':
            sb.append("\\\"");
            break;
          default:
            sb.append(ch);
            break;
        }
      } else if (ch >= 0xd800 && ch <= 0xdfff) {
        // surrogates
        escapeChar(sb, ch);
      } else {
        sb.append(ch);
      }
    }
    sb.append("\"");
  }

  private static final char[] HEX_TABLE = "0123456789ABCDEF".toCharArray();

  private static void escapeChar(StringBuilder sb, int ch) {
    sb.append("\\u");
    sb.append(HEX_TABLE[(ch >> 12) & 0x0f]);
    sb.append(HEX_TABLE[(ch >> 8) & 0x0f]);
    sb.append(HEX_TABLE[(ch >> 4) & 0x0f]);
    sb.append(HEX_TABLE[ch & 0x0f]);
  }
}
