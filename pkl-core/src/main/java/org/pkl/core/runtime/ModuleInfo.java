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
package org.pkl.core.runtime;

import com.oracle.truffle.api.source.SourceSection;
import java.net.URI;
import java.util.*;
import org.pkl.core.ModuleSchema;
import org.pkl.core.PClass;
import org.pkl.core.TypeAlias;
import org.pkl.core.ast.MemberNode;
import org.pkl.core.ast.expression.unary.AbstractImportNode;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.module.ResolvedModuleKey;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;

public final class ModuleInfo {
  private final SourceSection headerSection;
  private final SourceSection sourceSection;
  private final @Nullable SourceSection docComment;
  private final String moduleName;
  private final ModuleKey moduleKey;
  private final ResolvedModuleKey resolvedModuleKey;
  private final boolean isAmend;

  @LateInit private List<VmTyped> annotations;

  @LateInit private VmTyped __mirror;
  private final Object mirrorLock = new Object();

  @LateInit private ModuleSchema __moduleSchema;
  private final Object moduleSchemaLock = new Object();

  public ModuleInfo(
      SourceSection sourceSection,
      SourceSection headerSection,
      @Nullable SourceSection docComment,
      String moduleName,
      ModuleKey moduleKey,
      ResolvedModuleKey resolvedModuleKey,
      boolean isAmend) {

    this.sourceSection = sourceSection;
    this.headerSection = headerSection;
    this.docComment = docComment;
    this.moduleName = moduleName;
    this.moduleKey = moduleKey;
    this.resolvedModuleKey = resolvedModuleKey;
    this.isAmend = isAmend;
  }

  public void initAnnotations(List<VmTyped> annotations) {
    assert this.annotations == null;
    this.annotations = annotations;
  }

  public List<VmTyped> getAnnotations() {
    assert annotations != null;
    return annotations;
  }

  public SourceSection getSourceSection() {
    return sourceSection;
  }

  public SourceSection getHeaderSection() {
    return headerSection;
  }

  public @Nullable SourceSection getDocComment() {
    return docComment;
  }

  public String getModuleName() {
    return moduleName;
  }

  public ModuleKey getModuleKey() {
    return moduleKey;
  }

  public ResolvedModuleKey getResolvedModuleKey() {
    return resolvedModuleKey;
  }

  public VmTyped getMirror(VmTyped module) {
    synchronized (mirrorLock) {
      assert (module.getModuleInfo() == this);

      if (__mirror == null) {
        __mirror = MirrorFactories.moduleFactory.create(module);
      }
      return __mirror;
    }
  }

  public ModuleSchema getModuleSchema(VmTyped module) {
    synchronized (moduleSchemaLock) {
      assert (module.getModuleInfo() == this);

      if (__moduleSchema == null) {
        var parent = module.getParent();
        // every module has a superclass and hence also a parent
        assert parent != null;

        ModuleSchema supermodule = null;
        if (parent != BaseModule.getModuleClass().getPrototype()) {
          supermodule = parent.getModuleInfo().getModuleSchema(parent);
        }

        var imports = new LinkedHashMap<String, URI>();
        var classes = new LinkedHashMap<String, PClass>();
        var typeAliases = new LinkedHashMap<String, TypeAlias>();

        for (var propertyDef : EconomicMaps.getValues(module.getMembers())) {
          if (propertyDef.isImport()) {
            MemberNode memberNode = propertyDef.getMemberNode();
            assert memberNode != null; // import is never a constant
            var importNode = (AbstractImportNode) memberNode.getBodyNode();
            var importUri = importNode.getImportUri();
            imports.put(propertyDef.getName().toString(), importUri);
            continue;
          }

          if (propertyDef.isLocal()) continue;

          if (propertyDef.isClass()) {
            var clazz = (VmClass) module.getCachedValue(propertyDef.getName());
            if (clazz == null) {
              clazz = (VmClass) propertyDef.getCallTarget().call(module, module);
            }
            classes.put(clazz.getSimpleName(), clazz.export());
            continue;
          }

          if (propertyDef.isTypeAlias()) {
            var typeAlias = (VmTypeAlias) module.getCachedValue(propertyDef.getName());
            if (typeAlias == null) {
              typeAlias = (VmTypeAlias) propertyDef.getCallTarget().call(module, module);
            }
            typeAliases.put(typeAlias.getSimpleName(), typeAlias.export());
          }
        }

        __moduleSchema =
            new ModuleSchema(
                moduleKey.getUri(),
                moduleName,
                isAmend,
                supermodule,
                module.getVmClass().export(),
                VmUtils.exportDocComment(module.getModuleInfo().docComment),
                VmUtils.exportAnnotations(module.getModuleInfo().annotations),
                classes,
                typeAliases,
                imports);
      }

      return __moduleSchema;
    }
  }

  /** Tells whether this module amends another module. */
  public boolean isAmend() {
    return isAmend;
  }
}
