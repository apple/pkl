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
package org.pkl.core.util.msgpack.core;

import static org.pkl.core.util.msgpack.core.Preconditions.checkArgument;

/** Header of the Extension types */
@SuppressWarnings("ALL")
public class ExtensionTypeHeader {
  private final byte type;
  private final int length;

  /**
   * Create an extension type header Example:
   *
   * <pre>{@code
   * import org.pkl.core.util.messagepack.core.ExtensionTypeHeader;
   * import static org.pkl.core.util.messagepack.core.ExtensionTypeHeader.checkedCastToByte;
   * ...
   * ExtensionTypeHeader header = new ExtensionTypeHeader(checkedCastToByte(0x01), 32);
   * ...
   * }</pre>
   *
   * @param type extension type (byte). You can check the valid byte range with {@link
   *     #checkedCastToByte(int)} method.
   * @param length extension type data length
   */
  public ExtensionTypeHeader(byte type, int length) {
    checkArgument(length >= 0, "length must be >= 0");
    this.type = type;
    this.length = length;
  }

  public static byte checkedCastToByte(int code) {
    checkArgument(
        Byte.MIN_VALUE <= code && code <= Byte.MAX_VALUE,
        "Extension type code must be within the range of byte");
    return (byte) code;
  }

  public byte getType() {
    return type;
  }

  public boolean isTimestampType() {
    return type == MessagePack.Code.EXT_TIMESTAMP;
  }

  public int getLength() {
    return length;
  }

  @Override
  public int hashCode() {
    return (type + 31) * 31 + length;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ExtensionTypeHeader) {
      ExtensionTypeHeader other = (ExtensionTypeHeader) obj;
      return this.type == other.type && this.length == other.length;
    }
    return false;
  }

  @Override
  public String toString() {
    return String.format("ExtensionTypeHeader(type:%d, length:%,d)", type, length);
  }
}
