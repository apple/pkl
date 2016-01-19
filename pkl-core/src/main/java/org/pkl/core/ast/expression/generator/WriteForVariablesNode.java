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
package org.pkl.core.ast.expression.generator;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.EconomicMaps;

public final class WriteForVariablesNode extends ExpressionNode {
  private final int[] auxiliarySlots;
  @Child private ExpressionNode childNode;

  public WriteForVariablesNode(int[] auxiliarySlots, ExpressionNode childNode) {
    this.auxiliarySlots = auxiliarySlots;
    this.childNode = childNode;
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    var extraStorage = VmUtils.getOwner(frame).getExtraStorage();
    assert extraStorage instanceof UnmodifiableEconomicMap;

    @SuppressWarnings("unchecked")
    var forBindings = (UnmodifiableEconomicMap<Object, Object[]>) extraStorage;
    var bindings = EconomicMaps.get(forBindings, VmUtils.getMemberKey(frame));
    assert bindings != null;
    assert bindings.length == auxiliarySlots.length;

    for (var i = 0; i < auxiliarySlots.length; i++) {
      frame.setAuxiliarySlot(auxiliarySlots[i], bindings[i]);
    }

    return childNode.executeGeneric(frame);
  }
}
