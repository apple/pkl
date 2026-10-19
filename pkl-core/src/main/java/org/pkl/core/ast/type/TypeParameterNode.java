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
package org.pkl.core.ast.type;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import java.util.Arrays;
import java.util.List;
import org.pkl.core.TypeParameter.Variance;
import org.pkl.core.ast.PklNode;

public final class TypeParameterNode extends PklNode {

  private final Variance variance;
  private final String name;
  private final int index;
  private final int slot;

  public TypeParameterNode(
      SourceSection sourceSection, int slot, int index, String name, Variance variance) {
    super(sourceSection);
    this.slot = slot;
    this.index = index;
    this.name = name;
    this.variance = variance;
  }

  public Variance getVariance() {
    return variance;
  }

  public String getName() {
    return name;
  }

  public int getIndex() {
    return index;
  }

  public int getSlot() {
    return slot;
  }

  public org.pkl.core.TypeParameter export() {
    return new org.pkl.core.TypeParameter(variance, name, index);
  }

  public static List<org.pkl.core.TypeParameter> export(TypeParameterNode[] typeParameters) {
    return Arrays.stream(typeParameters).map(TypeParameterNode::export).toList();
  }

  public void executeAndSet(VirtualFrame frame, Object arg) {
    // TODO future: add type bounds checking here
    frame.setObject(slot, arg);
  }

  public enum OwnerType {
    CLASS,
    METHOD,
    TYPEALIAS
  }
}
