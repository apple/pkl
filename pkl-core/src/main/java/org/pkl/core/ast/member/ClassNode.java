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
package org.pkl.core.ast.member;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.PClassInfo;
import org.pkl.core.TypeParameter;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;

@NodeInfo(shortName = "class")
public final class ClassNode extends ExpressionNode {
  private final SourceSection headerSection;
  private final @Nullable SourceSection docComment;
  @Children private final ExpressionNode[] annotationNodes;
  private final int modifiers;
  private final PClassInfo<?> classInfo;
  private final List<TypeParameter> typeParameters;
  private final @Nullable ModuleInfo moduleInfo;
  // null iff this class is pkl.base#Any
  @Child private @Nullable UnresolvedTypeNode unresolvedSupertypeNode;
  private final EconomicMap<Object, ObjectMember> prototypeMembers;
  @Children private final UnresolvedPropertyNode[] unresolvedPropertyNodes;
  @Children private final UnresolvedMethodNode[] unresolvedMethodNodes;

  @CompilationFinal @LateInit private VmClass cachedClass;

  public ClassNode(
      SourceSection section,
      SourceSection headerSection,
      @Nullable SourceSection docComment,
      ExpressionNode[] annotationNodes,
      int modifiers,
      PClassInfo<?> classInfo,
      List<TypeParameter> typeParameters,
      @Nullable ModuleInfo moduleInfo,
      @Nullable UnresolvedTypeNode unresolvedSupertypeNode,
      EconomicMap<Object, ObjectMember> prototypeMembers,
      UnresolvedPropertyNode[] unresolvedPropertyNodes,
      UnresolvedMethodNode[] unresolvedMethodNodes) {

    super(section);
    this.headerSection = headerSection;
    this.docComment = docComment;
    this.annotationNodes = annotationNodes;
    this.modifiers = modifiers;
    this.classInfo = classInfo;
    this.typeParameters = typeParameters;
    this.moduleInfo = moduleInfo;
    this.unresolvedSupertypeNode = unresolvedSupertypeNode;
    this.prototypeMembers = prototypeMembers;
    this.unresolvedPropertyNodes = unresolvedPropertyNodes;
    this.unresolvedMethodNodes = unresolvedMethodNodes;
  }

  @Override
  public VmClass executeGeneric(VirtualFrame frame) {
    // Break class resolution cycles by immediately returning the
    // (possibly not yet fully initialized) cached class instance on subsequent calls.
    // Caching of classes also guarantees that classes are singletons and can be compared by
    // identity,
    // which improves efficiency and performance (for example in shape checks).
    if (cachedClass != null) return cachedClass;

    CompilerDirectives.transferToInterpreter();

    var module = VmUtils.getTypedObjectReceiver(frame);

    VmTyped prototype;
    var isModuleClass = moduleInfo != null;
    if (isModuleClass) {
      // For module classes, the corresponding (uninitialized) module object is provided by the
      // caller of this frame.
      // This allows to conveniently make stdlib module objects and their members accessible to
      // nodes
      // via static final fields without having to fear recursive field initialization.
      prototype = module;
      prototype.setExtraStorage(moduleInfo);
      prototype.addProperties(prototypeMembers);
    } else {
      prototype =
          new VmTyped(
              frame.materialize(),
              null, // initialized later by VmClass
              null, // initialized later by Vmclass
              prototypeMembers);
    }

    var annotations = new ArrayList<VmTyped>(annotationNodes.length);
    if (moduleInfo != null) moduleInfo.initAnnotations(annotations);

    // Cache the (not yet fully initialized) class before making any `resolveXYZ()`
    // or `node.execute()` calls as those may result in recursive calls to this method.
    cachedClass =
        new VmClass(
            sourceSection,
            headerSection,
            docComment,
            annotations,
            modifiers,
            classInfo,
            typeParameters,
            prototype);

    if (unresolvedSupertypeNode != null) {
      var supertypeNode = unresolvedSupertypeNode.execute(frame);
      var superclass = supertypeNode.getVmClass();

      checkSupertype(supertypeNode, superclass);
      cachedClass.initSupertype(supertypeNode, superclass);
    }

    // The superclass resolved above may not itself have completed the below initializations yet.
    // That's because these initializations may have indirectly or directly triggered
    // resolution of this class, in which case the `resolveSuperclass()` call above
    // will have returned the partially initialized `cachedClass` of the superclass.
    // As a consequence, initializations that require a fully initialized class hierarchy
    // are done lazily in VmClass rather than here.
    // A fully initialized class hierarchy is only required for initialization of internal caches,
    // which is guaranteed to succeed (no impact on eager vs. lazy error reporting) and easy to
    // defer.

    VmUtils.evaluateAnnotations(frame, annotationNodes, annotations);

    for (var node : unresolvedPropertyNodes) {
      cachedClass.addProperty(node.execute(frame, cachedClass));
    }

    for (var node : unresolvedMethodNodes) {
      cachedClass.addMethod(node.execute(frame, cachedClass));
    }

    cachedClass.notifyInitialized();

    return cachedClass;
  }

  private void checkSupertype(TypeNode supertypeNode, @Nullable VmClass superclass) {
    if (superclass == null) {
      throw exceptionBuilder()
          .evalError("invalidSupertype", supertypeNode.getSourceSection().getCharacters())
          .withSourceSection(supertypeNode.getSourceSection())
          .build();
    }
    if (moduleInfo != null) {
      if (cachedClass == superclass) {
        throw exceptionBuilder()
            .evalError("moduleCannotExtendSelf", moduleInfo.getModuleName())
            .withSourceSection(supertypeNode.getSourceSection())
            .build();
      }
      if (superclass.isClosed()) {
        throw exceptionBuilder()
            .evalError("cannotExtendFinalModule", superclass.getModuleName())
            .withSourceSection(supertypeNode.getSourceSection())
            .build();
      }
    } else {
      if (cachedClass == superclass) {
        throw exceptionBuilder()
            .evalError("classCannotExtendSelf", superclass.getDisplayName())
            .withSourceSection(supertypeNode.getSourceSection())
            .build();
      }
      if (superclass.isClosed()) {
        throw exceptionBuilder()
            .evalError("cannotExtendFinalClass", superclass.getDisplayName())
            .withSourceSection(supertypeNode.getSourceSection())
            .build();
      }
      if (superclass.isExternal() && !classInfo.isStandardLibraryClass()) {
        throw new VmExceptionBuilder()
            .evalError("cannotExtendExternalClass", superclass.getDisplayName())
            .withSourceSection(supertypeNode.getSourceSection())
            .build();
      }
    }
  }
}
