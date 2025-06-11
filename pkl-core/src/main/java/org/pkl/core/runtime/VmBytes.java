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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import org.pkl.core.DataSizeUnit;
import org.pkl.core.ast.ByteConstantValueNode;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.util.ByteArrayUtils;
import org.pkl.core.util.Nullable;

@ValueType
public final class VmBytes extends VmValue implements Iterable<Long> {

  private @Nullable VmList vmList;
  private @Nullable String base64;
  private @Nullable String hex;
  private final byte[] bytes;
  private @Nullable VmDataSize size;

  public static VmBytes EMPTY = new VmBytes(new byte[0]);

  @TruffleBoundary
  public static VmBytes createFromConstantNodes(ExpressionNode[] elements) {
    if (elements.length == 0) {
      return EMPTY;
    }
    var bytes = new byte[elements.length];
    for (var i = 0; i < elements.length; i++) {
      var exprNode = elements[i];
      // guaranteed by AstBuilder
      assert exprNode instanceof ByteConstantValueNode;
      bytes[i] = ((ByteConstantValueNode) exprNode).getByteValue();
    }
    return new VmBytes(bytes);
  }

  public VmBytes(byte[] bytes) {
    this.bytes = bytes;
  }

  public VmBytes(VmList vmList, byte[] bytes) {
    this.vmList = vmList;
    this.bytes = bytes;
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getBytesClass();
  }

  @Override
  public void force(boolean allowUndefinedValues) {
    // do nothing
  }

  @Override
  public byte[] export() {
    return bytes;
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitBytes(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertBytes(this, path);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof VmBytes vmBytes)) {
      return false;
    }
    return Arrays.equals(bytes, vmBytes.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }

  public byte[] getBytes() {
    return bytes;
  }

  public long get(long index) {
    return getBytes()[(int) index];
  }

  public VmBytes concatenate(VmBytes right) {
    if (bytes.length == 0) return right;
    if (right.bytes.length == 0) return this;

    var newBytes = new byte[bytes.length + right.bytes.length];
    System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
    System.arraycopy(right.bytes, 0, newBytes, bytes.length, right.bytes.length);
    return new VmBytes(newBytes);
  }

  public VmList toList() {
    if (vmList == null) {
      vmList = VmList.create(bytes);
    }
    return vmList;
  }

  public String base64() {
    if (base64 == null) {
      base64 = ByteArrayUtils.base64(bytes);
    }
    return base64;
  }

  public String hex() {
    if (hex == null) {
      hex = ByteArrayUtils.toHex(bytes);
    }
    return hex;
  }

  public int getLength() {
    return bytes.length;
  }

  public VmDataSize getSize() {
    if (size == null) {
      if (getLength() == 0) {
        // avoid log10(0), which gives us -Infinity
        size = new VmDataSize(0, DataSizeUnit.BYTES);
      } else {
        var magnitude = (int) Math.floor(Math.log10(getLength()));
        var unit =
            switch (magnitude) {
              case 0, 1, 2 -> DataSizeUnit.BYTES;
              case 3, 4, 5 -> DataSizeUnit.KILOBYTES;
              case 6, 7, 8 -> DataSizeUnit.MEGABYTES;
              case 9, 10, 11 -> DataSizeUnit.GIGABYTES;
              // in practice, can never happen (Java can only hold at most math.maxInt bytes).
              case 12, 13, 14 -> DataSizeUnit.TERABYTES;
              default -> DataSizeUnit.PETABYTES;
            };
        size = new VmDataSize(getLength(), DataSizeUnit.BYTES).convertTo(unit);
      }
    }
    return size;
  }

  @Override
  public String toString() {
    var sb = new StringBuilder("Bytes(");
    var isFirst = true;
    for (var byt : bytes) {
      if (isFirst) {
        isFirst = false;
      } else {
        sb.append(", ");
      }
      sb.append(Byte.toUnsignedInt(byt));
    }
    sb.append(")");
    return sb.toString();
  }

  @Override
  public Iterator<Long> iterator() {
    return new PrimitiveIterator.OfLong() {
      int index = 0;

      @Override
      public boolean hasNext() {
        return index < bytes.length;
      }

      @Override
      public long nextLong() {
        if (!hasNext()) {
          CompilerDirectives.transferToInterpreter();
          throw new NoSuchElementException();
        }
        var result = Byte.toUnsignedLong(bytes[index]);
        index += 1;
        return result;
      }
    };
  }
}
