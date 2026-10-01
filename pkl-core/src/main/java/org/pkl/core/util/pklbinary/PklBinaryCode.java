/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import org.jspecify.annotations.Nullable;

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
  REFERENCE((byte) 0x20),

  PROPERTY((byte) 0x10),
  ENTRY((byte) 0x11),
  ELEMENT((byte) 0x12),

  TYPE_UNKNOWN((byte) 0x40),
  TYPE_NOTHING((byte) 0x41),
  TYPE_STRING_LITERAL((byte) 0x42),
  TYPE_CLASS((byte) 0x43),
  TYPE_MODULE((byte) 0x44),
  TYPE_THIS((byte) 0x45),
  TYPE_NULLABLE((byte) 0x46),
  TYPE_CONSTRAINED((byte) 0x47),
  TYPE_TYPEALIAS((byte) 0x48),
  TYPE_UNION((byte) 0x49),
  TYPE_VARIABLE((byte) 0x4A);

  private final byte code;

  PklBinaryCode(byte code) {
    this.code = code;
  }

  public byte getCode() {
    return code;
  }

  public static @Nullable PklBinaryCode fromInt(int value) {
    return switch (value) {
      case 0x01 -> OBJECT;
      case 0x02 -> MAP;
      case 0x03 -> MAPPING;
      case 0x04 -> LIST;
      case 0x05 -> LISTING;
      case 0x06 -> SET;
      case 0x07 -> DURATION;
      case 0x08 -> DATASIZE;
      case 0x09 -> PAIR;
      case 0x0A -> INTSEQ;
      case 0x0B -> REGEX;
      case 0x0C -> CLASS;
      case 0x0D -> TYPEALIAS;
      case 0x0E -> FUNCTION;
      case 0x0F -> BYTES;
      case 0x20 -> REFERENCE;

      case 0x10 -> PROPERTY;
      case 0x11 -> ENTRY;
      case 0x12 -> ELEMENT;

      case 0x40 -> TYPE_UNKNOWN;
      case 0x41 -> TYPE_NOTHING;
      case 0x42 -> TYPE_STRING_LITERAL;
      case 0x43 -> TYPE_CLASS;
      case 0x44 -> TYPE_MODULE;
      case 0x45 -> TYPE_THIS;
      case 0x46 -> TYPE_NULLABLE;
      case 0x47 -> TYPE_CONSTRAINED;
      case 0x48 -> TYPE_TYPEALIAS;
      case 0x49 -> TYPE_UNION;
      case 0x4A -> TYPE_VARIABLE;

      default -> null;
    };
  }
}
