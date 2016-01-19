/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core;

import static org.pkl.core.DataSizeUnit.*;

import java.util.Objects;
import org.pkl.core.util.MathUtils;
import org.pkl.core.util.Nullable;

/** Java representation of a {@code pkl.base#DataSize} value. */
public final strictfp class DataSize implements Value {
  private static final long serialVersionUID = 0L;

  private final double value;
  private final DataSizeUnit unit;

  /** Constructs a new data size with the given value and unit. */
  public DataSize(double value, DataSizeUnit unit) {
    this.value = value;
    this.unit = Objects.requireNonNull(unit, "unit");
  }

  /** Constructs a new data size with the given value and unit {@link DataSizeUnit#BYTES}. */
  public static DataSize ofBytes(double value) {
    return new DataSize(value, BYTES);
  }

  /** Constructs a new data size with the given value and unit {@link DataSizeUnit#KILOBYTES}. */
  public static DataSize ofKilobytes(double value) {
    return new DataSize(value, KILOBYTES);
  }

  /** Constructs a new data size with the given value and unit {@link DataSizeUnit#KIBIBYTES}. */
  public static DataSize ofKibibytes(double value) {
    return new DataSize(value, KIBIBYTES);
  }

  /** Constructs a new data size with the given value and unit {@link DataSizeUnit#MEGABYTES}. */
  public static DataSize ofMegabytes(double value) {
    return new DataSize(value, MEGABYTES);
  }

  /** Constructs a new data size with the given value and unit {@link DataSizeUnit#MEBIBYTES}. */
  public static DataSize ofMebibytes(double value) {
    return new DataSize(value, MEBIBYTES);
  }

  /** Constructs a new data size with the given value and unit {@link DataSizeUnit#GIGABYTES}. */
  public static DataSize ofGigabytes(double value) {
    return new DataSize(value, GIGABYTES);
  }

  /** Constructs a new data size with the given value and unit {@link DataSizeUnit#GIBIBYTES}. */
  public static DataSize ofGibibytes(double value) {
    return new DataSize(value, GIBIBYTES);
  }

  /** Constructs a new data size with the given value and unit {@link DataSizeUnit#TERABYTES}. */
  public static DataSize ofTerabytes(double value) {
    return new DataSize(value, TERABYTES);
  }

  /** Constructs a new data size with the given value and unit {@link DataSizeUnit#TEBIBYTES}. */
  public static DataSize ofTebibytes(double value) {
    return new DataSize(value, TEBIBYTES);
  }

  /** Constructs a new data size with the given value and unit {@link DataSizeUnit#PETABYTES}. */
  public static DataSize ofPetabytes(double value) {
    return new DataSize(value, PETABYTES);
  }

  /** Constructs a new data size with the given value and unit {@link DataSizeUnit#PEBIBYTES}. */
  public static DataSize ofPebibytes(double value) {
    return new DataSize(value, PEBIBYTES);
  }

  /** Returns the value of this data size. The value is relative to the unit and may be negative. */
  public double getValue() {
    return value;
  }

  /** Returns the unit of this data size. */
  public DataSizeUnit getUnit() {
    return unit;
  }

  /** Returns the value of this data size measured in {@link DataSizeUnit#BYTES}. */
  public double inBytes() {
    return convertValueTo(BYTES);
  }

  /** Returns the value of this data size measured in {@link DataSizeUnit#KILOBYTES}. */
  public double inKilobytes() {
    return convertValueTo(KILOBYTES);
  }

  /** Returns the value of this data size measured in {@link DataSizeUnit#KIBIBYTES}. */
  public double inKibibytes() {
    return convertValueTo(KIBIBYTES);
  }

  /** Returns the value of this data size measured in {@link DataSizeUnit#MEGABYTES}. */
  public double inMegabytes() {
    return convertValueTo(MEGABYTES);
  }

  /** Returns the value of this data size measured in {@link DataSizeUnit#MEBIBYTES}. */
  public double inMebibytes() {
    return convertValueTo(MEBIBYTES);
  }

  /** Returns the value of this data size measured in {@link DataSizeUnit#GIGABYTES}. */
  public double inGigabytes() {
    return convertValueTo(GIGABYTES);
  }

  /** Returns the value of this data size measured in {@link DataSizeUnit#GIBIBYTES}. */
  public double inGibibytes() {
    return convertValueTo(GIBIBYTES);
  }

  /** Returns the value of this data size measured in {@link DataSizeUnit#TERABYTES}. */
  public double inTerabytes() {
    return convertValueTo(TERABYTES);
  }

  /** Returns the value of this data size measured in {@link DataSizeUnit#TEBIBYTES}. */
  public double inTebibytes() {
    return convertValueTo(TEBIBYTES);
  }

  /** Returns the value of this data size measured in {@link DataSizeUnit#PETABYTES}. */
  public double inPetabytes() {
    return convertValueTo(PETABYTES);
  }

  /** Returns the value of this data size measured in {@link DataSizeUnit#PEBIBYTES}. */
  public double inPebibytes() {
    return convertValueTo(PEBIBYTES);
  }

  /** Returns the value of this data size measured in whole {@link DataSizeUnit#BYTES}. */
  public long inWholeBytes() {
    return Math.round(inBytes());
  }

  /** Returns the value of this data size measured in whole {@link DataSizeUnit#KILOBYTES}. */
  public long inWholeKilobytes() {
    return Math.round(inKilobytes());
  }

  /** Returns the value of this data size measured in whole {@link DataSizeUnit#KIBIBYTES}. */
  public long inWholeKibibytes() {
    return Math.round(inKibibytes());
  }

  /** Returns the value of this data size measured in whole {@link DataSizeUnit#MEGABYTES}. */
  public long inWholeMegabytes() {
    return Math.round(inMegabytes());
  }

  /** Returns the value of this data size measured in whole {@link DataSizeUnit#MEBIBYTES}. */
  public long inWholeMebibytes() {
    return Math.round(inMebibytes());
  }

  /** Returns the value of this data size measured in whole {@link DataSizeUnit#GIGABYTES}. */
  public long inWholeGigabytes() {
    return Math.round(inGigabytes());
  }

  /** Returns the value of this data size measured in whole {@link DataSizeUnit#GIBIBYTES}. */
  public long inWholeGibibytes() {
    return Math.round(inGibibytes());
  }

  /** Returns the value of this data size measured in whole {@link DataSizeUnit#TERABYTES}. */
  public long inWholeTerabytes() {
    return Math.round(inTerabytes());
  }

  /** Returns the value of this data size measured in whole {@link DataSizeUnit#TEBIBYTES}. */
  public long inWholeTebibytes() {
    return Math.round(inTebibytes());
  }

  /** Returns the value of this data size measured in whole {@link DataSizeUnit#PETABYTES}. */
  public long inWholePetabytes() {
    return Math.round(inPetabytes());
  }

  /** Returns the value of this data size measured in whole {@link DataSizeUnit#PEBIBYTES}. */
  public long inWholePebibytes() {
    return Math.round(inPebibytes());
  }

  /** Returns a new data size with the given unit and this value converted to the given unit. */
  public DataSize convertTo(DataSizeUnit other) {
    return new DataSize(convertValueTo(other), other);
  }

  /** Returns the value of this data size converted to the given unit. */
  public double convertValueTo(DataSizeUnit other) {
    return value * unit.getBytes() / other.getBytes();
  }

  /** {@inheritDoc} */
  @Override
  public void accept(ValueVisitor visitor) {
    visitor.visitDataSize(this);
  }

  /** {@inheritDoc} */
  @Override
  public <T> T accept(ValueConverter<T> converter) {
    return converter.convertDataSize(this);
  }

  /** {@inheritDoc} */
  @Override
  public PClassInfo<?> getClassInfo() {
    return PClassInfo.DataSize;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof DataSize)) return false;

    var other = (DataSize) obj;
    return convertValueTo(DataSizeUnit.BYTES) == other.convertValueTo(DataSizeUnit.BYTES);
  }

  @Override
  public int hashCode() {
    return Double.hashCode(convertValueTo(DataSizeUnit.BYTES));
  }

  @Override
  public String toString() {
    return MathUtils.isMathematicalInteger(value) ? (long) value + "." + unit : value + "." + unit;
  }
}
