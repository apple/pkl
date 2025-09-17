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
package org.pkl.core.util.pklbinary;

import org.pkl.core.util.Nullable;

public enum PklBinaryTypes {
  CODE_OBJECT((byte) 0x01),
  CODE_MAP((byte) 0x02),
  CODE_MAPPING((byte) 0x03),
  CODE_LIST((byte) 0x04),
  CODE_LISTING((byte) 0x05),
  CODE_SET((byte) 0x06),
  CODE_DURATION((byte) 0x07),
  CODE_DATASIZE((byte) 0x08),
  CODE_PAIR((byte) 0x09),
  CODE_INTSEQ((byte) 0x0A),
  CODE_REGEX((byte) 0x0B),
  CODE_CLASS((byte) 0x0C),
  CODE_TYPEALIAS((byte) 0x0D),
  CODE_FUNCTION((byte) 0x0E),
  CODE_BYTES((byte) 0x0F),

  CODE_PROPERTY((byte) 0x10),
  CODE_ENTRY((byte) 0x11),
  CODE_ELEMENT((byte) 0x12);

  private final byte code;

  PklBinaryTypes(byte code) {
    this.code = code;
  }

  public byte getCode() {
    return code;
  }

  public static @Nullable PklBinaryTypes fromInt(int value) {
    return switch (value) {
      case 0x01 -> PklBinaryTypes.CODE_OBJECT;
      case 0x02 -> PklBinaryTypes.CODE_MAP;
      case 0x03 -> PklBinaryTypes.CODE_MAPPING;
      case 0x04 -> PklBinaryTypes.CODE_LIST;
      case 0x05 -> PklBinaryTypes.CODE_LISTING;
      case 0x06 -> PklBinaryTypes.CODE_SET;
      case 0x07 -> PklBinaryTypes.CODE_DURATION;
      case 0x08 -> PklBinaryTypes.CODE_DATASIZE;
      case 0x09 -> PklBinaryTypes.CODE_PAIR;
      case 0x0A -> PklBinaryTypes.CODE_INTSEQ;
      case 0x0B -> PklBinaryTypes.CODE_REGEX;
      case 0x0C -> PklBinaryTypes.CODE_CLASS;
      case 0x0D -> PklBinaryTypes.CODE_TYPEALIAS;
      case 0x0E -> PklBinaryTypes.CODE_FUNCTION;
      case 0x0F -> PklBinaryTypes.CODE_BYTES;

      case 0x10 -> PklBinaryTypes.CODE_PROPERTY;
      case 0x11 -> PklBinaryTypes.CODE_ENTRY;
      case 0x12 -> PklBinaryTypes.CODE_ELEMENT;
      default -> null;
    };
  }
}
