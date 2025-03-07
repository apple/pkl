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

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import java.util.Arrays;
import java.util.Base64;
import org.pkl.core.util.ByteArrayUtils;
import org.pkl.core.util.Nullable;

@ValueType
public final class VmBytes extends VmValue {

  private @Nullable VmList vmList;
  private @Nullable String base64;
  private @Nullable String hex;
  private final byte[] bytes;

  public VmBytes(byte[] bytes) {
    this.bytes = bytes;
  }

  public VmBytes(VmList vmList) {
    this.vmList = vmList;
    this.bytes = new byte[vmList.getLength()];
    for (var i = 0; i < vmList.getLength(); i++) {
      bytes[i] = ((Long) vmList.get(i)).byteValue();
    }
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

  public VmList vmList() {
    if (vmList == null) {
      vmList = VmList.create(bytes);
    }
    return vmList;
  }

  public String base64() {
    if (base64 == null) {
      base64 = Base64.getEncoder().encodeToString(bytes);
    }
    return base64;
  }

  public String hex() {
    if (hex == null) {
      hex = ByteArrayUtils.toHex(bytes);
    }
    return hex;
  }

  @Override
  public String toString() {
    return "Bytes(\"" + base64() + "\")";
  }
}
