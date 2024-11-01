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
package org.pkl.core.util.msgpack.value;

import java.math.BigInteger;
import org.pkl.core.util.msgpack.core.MessageFormat;

/**
 * Representation of MessagePack's Integer type.
 *
 * <p>MessagePack's Integer type can represent from -2<sup>63</sup> to 2<sup>64</sup>-1.
 */
public interface IntegerValue extends NumberValue {
  /** Returns true if the value is in the range of [-2<sup>7</sup> to 2<sup>7</sup>-1]. */
  boolean isInByteRange();

  /** Returns true if the value is in the range of [-2<sup>15</sup> to 2<sup>15</sup>-1] */
  boolean isInShortRange();

  /** Returns true if the value is in the range of [-2<sup>31</sup> to 2<sup>31</sup>-1] */
  boolean isInIntRange();

  /** Returns true if the value is in the range of [-2<sup>63</sup> to 2<sup>63</sup>-1] */
  boolean isInLongRange();

  /**
   * Returns the most succinct MessageFormat type to represent this integer value.
   *
   * @return the smallest integer type of MessageFormat that is big enough to store the value.
   */
  MessageFormat mostSuccinctMessageFormat();

  /**
   * Returns the value as a {@code byte}, otherwise throws an exception.
   *
   * @throws MessageIntegerOverflowException If the value does not fit in the range of {@code byte}
   *     type.
   */
  byte asByte();

  /**
   * Returns the value as a {@code short}, otherwise throws an exception.
   *
   * @throws MessageIntegerOverflowException If the value does not fit in the range of {@code short}
   *     type.
   */
  short asShort();

  /**
   * Returns the value as an {@code int}, otherwise throws an exception.
   *
   * @throws MessageIntegerOverflowException If the value does not fit in the range of {@code int}
   *     type.
   */
  int asInt();

  /**
   * Returns the value as a {@code long}, otherwise throws an exception.
   *
   * @throws MessageIntegerOverflowException If the value does not fit in the range of {@code long}
   *     type.
   */
  long asLong();

  /** Returns the value as a {@code BigInteger}. */
  BigInteger asBigInteger();
}
