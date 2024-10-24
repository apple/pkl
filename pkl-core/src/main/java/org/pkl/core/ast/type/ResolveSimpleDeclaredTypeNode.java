/*
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.runtime.*;

public final class ResolveSimpleDeclaredTypeNode extends ResolveDeclaredTypeNode {
  private final Identifier typeName;
  private final boolean isBaseModule;

  public ResolveSimpleDeclaredTypeNode(
      SourceSection sourceSection, Identifier typeName, boolean isBaseModule) {

    super(sourceSection);
    this.typeName = typeName;
    this.isBaseModule = isBaseModule;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    CompilerDirectives.transferToInterpreter();

    var localTypeName = typeName.toLocalProperty();
    var enclosingModule = getEnclosingModule(VmUtils.getOwner(frame));

    // search enclosing module for local class/type alias or module import
    var result = getType(enclosingModule, localTypeName, sourceSection);
    if (result != null) return result;

    // search module hierarchy
    var currModule = enclosingModule;
    do {
      result = getType(currModule, typeName, sourceSection);
      if (result != null) return result;

      // search base module (after enclosing module, before parent modules)
      if (!isBaseModule && currModule == enclosingModule) {
        result = getType(BaseModule.getModule(), typeName, sourceSection);
        if (result != null) return result;
      }

      currModule = currModule.getParent();
    } while (currModule != null);

    throw exceptionBuilder()
        .evalError(
            "cannotFindSimpleType", typeName, enclosingModule.getModuleInfo().getModuleName())
        .build();
  }
}
