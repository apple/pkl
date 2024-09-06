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
package org.pkl.core.ast.type;

import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.util.Nullable;

public abstract class ResolveDeclaredTypeNode extends ExpressionNode {
  @Child private IndirectCallNode callNode = IndirectCallNode.create();

  protected ResolveDeclaredTypeNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  protected VmTyped getEnclosingModule(VmObjectLike initialOwner) {
    var curr = initialOwner;
    var next = curr.getEnclosingOwner();

    while (next != null) {
      curr = next;
      next = next.getEnclosingOwner();
    }

    assert curr.isModuleObject();
    return (VmTyped) curr;
  }

  protected VmTyped getImport(
      VmTyped module, Identifier importName, SourceSection importNameSection) {
    assert importName.isLocalProp();

    var member = module.getMember(importName);
    if (member == null) {
      throw exceptionBuilder()
          .evalError("cannotFindModuleImport", importName)
          .withSourceSection(importNameSection)
          .build();
    }

    if (!member.isImport()) {
      throw exceptionBuilder()
          .evalError("notAModuleImport", importName)
          .withSourceSection(importNameSection)
          .build();
    }

    if (member.isGlob()) {
      throw exceptionBuilder()
          .evalError("notAType", importName)
          .withSourceSection(importNameSection)
          .build();
    }

    assert member.getConstantValue() == null;
    var result = module.getCachedValue(importName);
    if (result == null) {
      result = callNode.call(member.getCallTarget(), module, module, importName);
      module.setCachedValue(importName, result, member);
    }
    return (VmTyped) result;
  }

  protected @Nullable Object getType(
      VmTyped module, Identifier typeName, SourceSection typeNameSection) {
    var member = module.getMember(typeName);
    if (member == null) return null;

    if (!member.isType()) {
      throw exceptionBuilder()
          .evalError("notAType", typeName)
          .withSourceSection(typeNameSection)
          .build();
    }

    assert member.getConstantValue() == null;
    var result = module.getCachedValue(typeName);
    if (result == null) {
      result = callNode.call(member.getCallTarget(), module, module, typeName);
      module.setCachedValue(typeName, result, member);
    }
    return result;
  }
}
