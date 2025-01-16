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
package org.pkl.core.ast.builder;

import com.oracle.truffle.api.source.Source;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.module.ModuleKeys;
import org.pkl.core.module.ResolvedModuleKey;
import org.pkl.core.newparser.ParserVisitor;
import org.pkl.core.newparser.cst.Module;
import org.pkl.core.runtime.ModuleInfo;
import org.pkl.core.runtime.ModuleResolver;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.stdlib.registry.ExternalMemberRegistry;
import org.pkl.core.stdlib.registry.MemberRegistryFactory;
import org.pkl.core.util.IoUtils;

public class AstBuilderNew extends ParserVisitor<Object> {
  protected final Source source;
  private final VmLanguage language;
  private final ModuleInfo moduleInfo;

  private final ModuleKey moduleKey;
  private final ModuleResolver moduleResolver;
  private final boolean isBaseModule;
  private final boolean isStdLibModule;
  private final ExternalMemberRegistry externalMemberRegistry;
  private final SymbolTable symbolTable;
  private final boolean isMethodReturnTypeChecked;

  public AstBuilderNew(
      Source source, VmLanguage language, ModuleInfo moduleInfo, ModuleResolver moduleResolver) {
    this.source = source;
    this.language = language;
    this.moduleInfo = moduleInfo;

    moduleKey = moduleInfo.getModuleKey();
    this.moduleResolver = moduleResolver;
    isBaseModule = ModuleKeys.isBaseModule(moduleKey);
    isStdLibModule = ModuleKeys.isStdLibModule(moduleKey);
    externalMemberRegistry = MemberRegistryFactory.get(moduleKey);
    symbolTable = new SymbolTable(moduleInfo);
    isMethodReturnTypeChecked = !isStdLibModule || IoUtils.isTestMode();
  }

  public static AstBuilderNew create(
      Source source,
      VmLanguage language,
      Module ctx,
      ModuleKey moduleKey,
      ResolvedModuleKey resolvedModuleKey,
      ModuleResolver moduleResolver) {
    throw new RuntimeException("not implemented");
  }
}
