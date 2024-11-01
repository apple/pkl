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

import java.io.IOException;
import org.pkl.core.util.msgpack.core.MessagePacker;
import org.pkl.core.util.msgpack.value.ImmutableNilValue;
import org.pkl.core.util.msgpack.value.NilValue;
import org.pkl.core.util.msgpack.value.Value;
import org.pkl.core.util.msgpack.value.ValueType;

/**
 * {@code ImmutableNilValueImpl} Implements {@code ImmutableNilValue}.
 *
 * <p>This class is a singleton. {@code ImmutableNilValueImpl.get()} is the only instances of this
 * class.
 *
 * @see NilValue
 */
public class ImmutableNilValueImpl extends AbstractImmutableValue implements ImmutableNilValue {
  private static ImmutableNilValue instance = new ImmutableNilValueImpl();

  public static ImmutableNilValue get() {
    return instance;
  }

  private ImmutableNilValueImpl() {}

  @Override
  public ValueType getValueType() {
    return ValueType.NIL;
  }

  @Override
  public ImmutableNilValue immutableValue() {
    return this;
  }

  @Override
  public ImmutableNilValue asNilValue() {
    return this;
  }

  @Override
  public void writeTo(MessagePacker pk) throws IOException {
    pk.packNil();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Value)) {
      return false;
    }
    return ((Value) o).isNilValue();
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public String toString() {
    return toJson();
  }

  @Override
  public String toJson() {
    return "null";
  }
}
