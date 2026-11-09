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
package org.pkl.core.ast;

import com.oracle.truffle.api.frame.VirtualFrame;

public class ByteConstantValueNode extends ExpressionNode implements ConstantNode {

  private final byte value;

  public ByteConstantValueNode(byte value) {
    this.value = value;
  }

  @Override
  public Long getValue() {
    return (long) value;
  }

  public byte getByteValue() {
    return value;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return getValue();
  }
}
