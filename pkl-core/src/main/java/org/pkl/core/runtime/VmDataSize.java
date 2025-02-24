/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.runtime;

import static java.util.Map.*;

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import java.util.*;
import org.pkl.core.DataSize;
import org.pkl.core.DataSizeUnit;
import org.pkl.core.Value;
import org.pkl.core.util.MathUtils;
import org.pkl.core.util.Nullable;

@ValueType
public final class VmDataSize extends VmValue implements Comparable<VmDataSize> {
  private static final Map<Identifier, DataSizeUnit> UNITS =
      Map.ofEntries(
          entry(Identifier.B, DataSizeUnit.BYTES),
          entry(Identifier.KB, DataSizeUnit.KILOBYTES),
          entry(Identifier.KIB, DataSizeUnit.KIBIBYTES),
          entry(Identifier.MB, DataSizeUnit.MEGABYTES),
          entry(Identifier.MIB, DataSizeUnit.MEBIBYTES),
          entry(Identifier.GB, DataSizeUnit.GIGABYTES),
          entry(Identifier.GIB, DataSizeUnit.GIBIBYTES),
          entry(Identifier.TB, DataSizeUnit.TERABYTES),
          entry(Identifier.TIB, DataSizeUnit.TEBIBYTES),
          entry(Identifier.PB, DataSizeUnit.PETABYTES),
          entry(Identifier.PIB, DataSizeUnit.PEBIBYTES));

  private final double value;
  private final DataSizeUnit unit;

  public VmDataSize(double value, DataSizeUnit unit) {
    this.value = value;
    this.unit = unit;
  }

  public static @Nullable DataSizeUnit toUnit(Identifier identifier) {
    return UNITS.get(identifier);
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getDataSizeClass();
  }

  public double getValue() {
    return value;
  }

  public DataSizeUnit getUnit() {
    return unit;
  }

  public VmDataSize add(VmDataSize other) {
    if (unit.ordinal() <= other.unit.ordinal()) {
      return new VmDataSize(convertValueTo(other.unit) + other.value, other.unit);
    }
    return new VmDataSize(value + other.convertValueTo(unit), unit);
  }

  public VmDataSize subtract(VmDataSize other) {
    if (unit.ordinal() <= other.unit.ordinal()) {
      return new VmDataSize(convertValueTo(other.unit) - other.value, other.unit);
    }
    return new VmDataSize(value - other.convertValueTo(unit), unit);
  }

  public VmDataSize multiply(double num) {
    return new VmDataSize(value * num, unit);
  }

  public VmDataSize divide(double num) {
    return new VmDataSize(value / num, unit);
  }

  public double divide(VmDataSize other) {
    // use same conversion strategy as add/subtract
    if (unit.ordinal() <= other.unit.ordinal()) {
      return convertValueTo(other.unit) / other.value;
    }
    return value / other.convertValueTo(unit);
  }

  public VmDataSize remainder(double num) {
    return new VmDataSize(value % num, unit);
  }

  public VmDataSize pow(double num) {
    return new VmDataSize(StrictMath.pow(value, num), unit);
  }

  public VmDataSize round() {
    return new VmDataSize(StrictMath.rint(value), unit);
  }

  public VmDataSize convertTo(DataSizeUnit unit) {
    return new VmDataSize(convertValueTo(unit), unit);
  }

  @Override
  public void force(boolean allowUndefinedValues) {
    // do nothing
  }

  @Override
  public Value export() {
    return new DataSize(value, unit);
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitDataSize(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertDataSize(this, path);
  }

  @Override
  public int compareTo(VmDataSize other) {
    // use same conversion strategy as add/subtract
    if (unit.ordinal() <= other.unit.ordinal()) {
      return Double.compare(convertValueTo(other.unit), other.value);
    }
    return Double.compare(value, other.convertValueTo(unit));
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof VmDataSize other)) return false;
    // converting to a fixed unit guarantees that equals() is commutative and consistent with
    // hashCode()
    return convertValueTo(DataSizeUnit.BYTES) == other.convertValueTo(DataSizeUnit.BYTES);
  }

  @Override
  int computeHashCode(Set<VmValue> seenValues) {
    return Double.hashCode(convertValueTo(DataSizeUnit.BYTES));
  }

  @Override
  public String toString() {
    return MathUtils.isMathematicalInteger(value) ? (long) value + "." + unit : value + "." + unit;
  }

  private double convertValueTo(DataSizeUnit other) {
    return value * unit.getBytes() / other.getBytes();
  }
}
