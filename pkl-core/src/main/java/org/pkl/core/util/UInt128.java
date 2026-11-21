/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util;

import com.oracle.truffle.api.ExactMath;

public record UInt128(long hi, long lo) {

  public static final int SIZE = 128;
  public static final UInt128 ZERO = new UInt128(0L, 0L);
  public static final UInt128 ONE = new UInt128(0L, 1L);
  public static final UInt128 MAX_VALUE = new UInt128(-1L, -1L);

  public static UInt128 fromBytes(byte[] bytes) {
    assert bytes.length == 16;
    return new UInt128(
        Byte.toUnsignedLong(bytes[0]) << 56
            | Byte.toUnsignedLong(bytes[1]) << 48
            | Byte.toUnsignedLong(bytes[2]) << 40
            | Byte.toUnsignedLong(bytes[3]) << 32
            | Byte.toUnsignedLong(bytes[4]) << 24
            | Byte.toUnsignedLong(bytes[5]) << 16
            | Byte.toUnsignedLong(bytes[6]) << 8
            | Byte.toUnsignedLong(bytes[7]),
        Byte.toUnsignedLong(bytes[8]) << 56
            | Byte.toUnsignedLong(bytes[9]) << 48
            | Byte.toUnsignedLong(bytes[10]) << 40
            | Byte.toUnsignedLong(bytes[11]) << 32
            | Byte.toUnsignedLong(bytes[12]) << 24
            | Byte.toUnsignedLong(bytes[13]) << 16
            | Byte.toUnsignedLong(bytes[14]) << 8
            | Byte.toUnsignedLong(bytes[15]));
  }

  public static UInt128 maskHi(long prefix) {
    if (prefix == 0) return ZERO;
    assert prefix <= SIZE && prefix > 0;
    var hi = -1L;
    var lo = -1L;
    if (prefix < Long.SIZE) {
      hi <<= Long.SIZE - prefix;
      lo = 0L;
    } else if (prefix == Long.SIZE) {
      lo = 0L;
    } else {
      lo <<= SIZE - prefix;
    }
    return new UInt128(hi, lo);
  }

  public static UInt128 maskLo(long suffix) {
    if (suffix == 0) return ZERO;
    assert suffix <= SIZE && suffix > 0;
    var hi = -1L;
    var lo = -1L;
    if (suffix < Long.SIZE) {
      lo >>>= Long.SIZE - suffix;
      hi = 0L;
    } else if (suffix == Long.SIZE) {
      hi = 0L;
    } else {
      hi >>>= SIZE - suffix;
    }
    return new UInt128(hi, lo);
  }

  public static UInt128 mask(long bit) {
    assert bit < SIZE && bit >= 0;
    var hi = 0L;
    var lo = 0L;
    if (bit < Long.SIZE) {
      lo = 1L << bit;
    } else {
      hi = 1L << (Long.SIZE - bit);
    }
    return new UInt128(hi, lo);
  }

  public byte[] getBytes() {
    return new byte[] {
      (byte) (hi >>> 56),
      (byte) ((hi >>> 48) & 0xff),
      (byte) ((hi >>> 40) & 0xff),
      (byte) ((hi >>> 32) & 0xff),
      (byte) ((hi >>> 24) & 0xff),
      (byte) ((hi >>> 16) & 0xff),
      (byte) ((hi >>> 8) & 0xff),
      (byte) (hi & 0xff),
      (byte) (lo >>> 56),
      (byte) ((lo >>> 48) & 0xff),
      (byte) ((lo >>> 40) & 0xff),
      (byte) ((lo >>> 32) & 0xff),
      (byte) ((lo >>> 24) & 0xff),
      (byte) ((lo >>> 16) & 0xff),
      (byte) ((lo >>> 8) & 0xff),
      (byte) (lo & 0xff),
    };
  }

  public UInt128 add(long other) {
    var sub = other < 0;
    var newLo = lo + other;
    var carry =
        sub
            ? (Long.compareUnsigned(newLo, lo) > 0) ? -1L : 0L
            : (Long.compareUnsigned(newLo, lo) < 0) ? 1L : 0L;
    var newHi = hi + carry;
    return new UInt128(newHi, newLo);
  }

  public UInt128 add(UInt128 other) {
    var newLo = lo + other.lo;
    var carry = (Long.compareUnsigned(newLo, lo) < 0) ? 1L : 0L;
    var newHi = hi + other.hi + carry;
    return new UInt128(newHi, newLo);
  }

  public UInt128 mult(long other) {
    var newLo = lo * other;
    var newHi =
        StrictMath.addExact(
            ExactMath.multiplyHighUnsigned(lo, other), StrictMath.multiplyExact(hi, other));
    return new UInt128(newHi, newLo);
  }

  public UInt128 and(UInt128 other) {
    return new UInt128(hi & other.hi, lo & other.lo);
  }

  public UInt128 or(UInt128 other) {
    return new UInt128(hi | other.hi, lo | other.lo);
  }

  public boolean lt(UInt128 other) {
    var comp = Long.compareUnsigned(hi, other.hi);
    return comp < 0 || (comp == 0 && Long.compareUnsigned(lo, other.lo) < 0);
  }

  public boolean le(UInt128 other) {
    var comp = Long.compareUnsigned(hi, other.hi);
    return comp < 0 || (comp == 0 && Long.compareUnsigned(lo, other.lo) <= 0);
  }
}
