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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.runtime.*;

public final class ResolveQualifiedDeclaredTypeNode extends ResolveDeclaredTypeNode {
  private final SourceSection moduleNameSection;
  private final SourceSection typeNameSection;
  private final Identifier moduleName;
  private final Identifier typeName;

  public ResolveQualifiedDeclaredTypeNode(
      SourceSection sourceSection,
      SourceSection moduleNameSection,
      SourceSection typeNameSection,
      Identifier moduleName,
      Identifier typeName) {

    super(sourceSection);
    this.moduleNameSection = moduleNameSection;
    this.typeNameSection = typeNameSection;
    this.moduleName = moduleName;
    this.typeName = typeName;

    assert moduleName.isLocalProp();
    assert typeName.isRegular();
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    CompilerDirectives.transferToInterpreter();

    var enclosingModule = getEnclosingModule(VmUtils.getOwner(frame));
    var importedModule = getImport(enclosingModule, moduleName, moduleNameSection);

    // search module hierarchy
    // (type declared in base module is accessible through extending and amending modules)
    for (var currModule = importedModule; currModule != null; currModule = currModule.getParent()) {
      var result = getType(currModule, typeName, sourceSection);
      if (result != null) return result;
    }

    throw exceptionBuilder()
        .evalError(
            "cannotFindQualifiedType", typeName, importedModule.getModuleInfo().getModuleName())
        .withSourceSection(typeNameSection)
        .build();
  }
}
