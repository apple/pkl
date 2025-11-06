/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.type.ResolveDeclaredTypeNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.ModuleInfo;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;

public abstract class AmendModuleNode extends SpecializedObjectLiteralNode {
  @Children private final ExpressionNode[] annotationNodes;
  private final ModuleInfo moduleInfo;

  public AmendModuleNode(
      SourceSection sourceSection,
      VmLanguage language,
      ExpressionNode[] annotationNodes,
      EconomicMap<Object, ObjectMember> properties,
      ModuleInfo moduleInfo) {

    super(sourceSection, language, "", false, null, new UnresolvedTypeNode[0], properties);
    this.annotationNodes = annotationNodes;
    this.moduleInfo = moduleInfo;
  }

  @Override
  @TruffleBoundary
  protected AmendModuleNode copy(ExpressionNode newParentNode) {
    throw exceptionBuilder().unreachableCode().build();
  }

  @Specialization
  protected VmTyped eval(VirtualFrame frame, VmTyped supermodule) {
    // receiver is empty module object
    var module = VmUtils.getTypedObjectReceiver(frame);

    if (module == supermodule) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("moduleCannotAmendSelf", moduleInfo.getModuleName())
          .build();
    }

    var _supermodule = supermodule;
    if (_supermodule.isNotInitialized()) {
      _supermodule = ResolveDeclaredTypeNode.findPrototypeModule(this, _supermodule);
      if (_supermodule == null) {
        throw exceptionBuilder().evalError("cyclicalModuleLoading").build();
      }
    }
    checkIsValidTypedAmendment(_supermodule);

    module.lateInitVmClass(_supermodule.getVmClass());
    module.lateInitParent(_supermodule);
    module.addProperties(members);

    module.setExtraStorage(moduleInfo);
    moduleInfo.initAnnotations(VmUtils.evaluateAnnotations(frame, annotationNodes));

    return module;
  }
}
