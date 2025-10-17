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

public enum PklBinaryCode {
  OBJECT((byte) 0x01),
  MAP((byte) 0x02),
  MAPPING((byte) 0x03),
  LIST((byte) 0x04),
  LISTING((byte) 0x05),
  SET((byte) 0x06),
  DURATION((byte) 0x07),
  DATASIZE((byte) 0x08),
  PAIR((byte) 0x09),
  INTSEQ((byte) 0x0A),
  REGEX((byte) 0x0B),
  CLASS((byte) 0x0C),
  TYPEALIAS((byte) 0x0D),
  FUNCTION((byte) 0x0E),
  BYTES((byte) 0x0F),

  PROPERTY((byte) 0x10),
  ENTRY((byte) 0x11),
  ELEMENT((byte) 0x12);

  private final byte code;

  PklBinaryCode(byte code) {
    this.code = code;
  }

  public byte getCode() {
    return code;
  }

  public static @Nullable PklBinaryCode fromInt(int value) {
    return switch (value) {
      case 0x01 -> PklBinaryCode.OBJECT;
      case 0x02 -> PklBinaryCode.MAP;
      case 0x03 -> PklBinaryCode.MAPPING;
      case 0x04 -> PklBinaryCode.LIST;
      case 0x05 -> PklBinaryCode.LISTING;
      case 0x06 -> PklBinaryCode.SET;
      case 0x07 -> PklBinaryCode.DURATION;
      case 0x08 -> PklBinaryCode.DATASIZE;
      case 0x09 -> PklBinaryCode.PAIR;
      case 0x0A -> PklBinaryCode.INTSEQ;
      case 0x0B -> PklBinaryCode.REGEX;
      case 0x0C -> PklBinaryCode.CLASS;
      case 0x0D -> PklBinaryCode.TYPEALIAS;
      case 0x0E -> PklBinaryCode.FUNCTION;
      case 0x0F -> PklBinaryCode.BYTES;

      case 0x10 -> PklBinaryCode.PROPERTY;
      case 0x11 -> PklBinaryCode.ENTRY;
      case 0x12 -> PklBinaryCode.ELEMENT;
      default -> null;
    };
  }
}
