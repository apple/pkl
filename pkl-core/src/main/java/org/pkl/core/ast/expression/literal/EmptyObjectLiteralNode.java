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
package org.pkl.core.ast.expression.literal;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.*;

/** Object literal node with empty body. Example: RHS of `x = foo {}` */
@ImportStatic(BaseModule.class)
@NodeChild(value = "parentNode", type = ExpressionNode.class)
public abstract class EmptyObjectLiteralNode extends ExpressionNode {
  protected EmptyObjectLiteralNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  protected abstract ExpressionNode getParentNode();

  @Specialization
  protected Object eval(VmClass parent) {
    if (parent.isListingClass()) {
      return VmListing.empty();
    }

    if (parent.isMappingClass()) {
      return VmMapping.empty();
    }

    if (parent.isDynamicClass()) {
      return VmDynamic.empty();
    }

    VmUtils.checkIsInstantiable(parent, getParentNode());
    return parent.getPrototype();
  }

  @Specialization
  protected Object eval(VmObjectLike parent) {
    return parent;
  }

  @Specialization
  protected Object eval(VmNull parent) {
    return parent.getDefaultValue();
  }

  @Fallback
  @TruffleBoundary
  protected void fallback(Object parent) {
    assert !(parent instanceof VmClass);
    VmUtils.checkIsInstantiable(VmUtils.getClass(parent), getParentNode());
    throw exceptionBuilder().unreachableCode().build();
  }
}
