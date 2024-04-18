/**
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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import java.util.*;
import org.pkl.core.*;
import org.pkl.core.util.DurationUtils;
import org.pkl.core.util.Nullable;

@ValueType
public final class VmDuration extends VmValue implements Comparable<VmDuration> {
  private static final Map<Identifier, DurationUnit> UNITS =
      Map.of(
          Identifier.NS, DurationUnit.NANOS,
          Identifier.US, DurationUnit.MICROS,
          Identifier.MS, DurationUnit.MILLIS,
          Identifier.S, DurationUnit.SECONDS,
          Identifier.MIN, DurationUnit.MINUTES,
          Identifier.H, DurationUnit.HOURS,
          Identifier.D, DurationUnit.DAYS);

  private final double value;
  private final DurationUnit unit;

  public VmDuration(double value, DurationUnit unit) {
    this.value = value;
    this.unit = unit;
  }

  public static @Nullable DurationUnit toUnit(Identifier identifier) {
    return UNITS.get(identifier);
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getDurationClass();
  }

  public VmDuration add(VmDuration other) {
    if (unit.ordinal() <= other.unit.ordinal()) {
      return new VmDuration(getValue(other.unit) + other.value, other.unit);
    }
    return new VmDuration(value + other.getValue(unit), unit);
  }

  public VmDuration subtract(VmDuration other) {
    if (unit.ordinal() <= other.unit.ordinal()) {
      return new VmDuration(getValue(other.unit) - other.value, other.unit);
    }
    return new VmDuration(value - other.getValue(unit), unit);
  }

  public double getValue() {
    return value;
  }

  public double getValue(DurationUnit other) {
    return value * unit.getNanos() / other.getNanos();
  }

  public DurationUnit getUnit() {
    return unit;
  }

  public VmDuration multiply(double num) {
    return new VmDuration(value * num, unit);
  }

  public VmDuration divide(double num) {
    return new VmDuration(value / num, unit);
  }

  public double divide(VmDuration other) {
    // use same conversion strategy as add/subtract
    if (unit.ordinal() <= other.unit.ordinal()) {
      return getValue(other.unit) / other.value;
    }
    return value / other.getValue(unit);
  }

  public VmDuration remainder(double num) {
    return new VmDuration(value % num, unit);
  }

  public VmDuration pow(double num) {
    return new VmDuration(StrictMath.pow(value, num), unit);
  }

  public VmDuration round() {
    return new VmDuration(StrictMath.rint(value), unit);
  }

  public VmDuration convertTo(DurationUnit unit) {
    return new VmDuration(getValue(unit), unit);
  }

  @Override
  public void force(boolean allowUndefinedValues) {
    // do nothing
  }

  @Override
  public Value export() {
    return new Duration(value, unit);
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitDuration(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertDuration(this, path);
  }

  @Override
  public int compareTo(VmDuration other) {
    // use same conversion strategy as add/subtract
    if (unit.ordinal() <= other.unit.ordinal()) {
      return Double.compare(getValue(other.unit), other.value);
    }
    return Double.compare(value, other.getValue(unit));
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof VmDuration)) return false;

    var other = (VmDuration) obj;
    // converting to a fixed unit guarantees that equals() is commutative and consistent with
    // hashCode()
    return getValue(DurationUnit.NANOS) == other.getValue(DurationUnit.NANOS);
  }

  @Override
  public int hashCode() {
    return Double.hashCode(getValue(DurationUnit.NANOS));
  }

  @Override
  public String toString() {
    return DurationUtils.toPklString(value, unit);
  }
}
