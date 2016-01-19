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
package org.pkl.core.runtime;

import com.oracle.truffle.api.nodes.Node;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.pkl.core.Release;
import org.pkl.core.Version;
import org.pkl.core.parser.antlr.PklParser.*;
import org.pkl.core.util.Nullable;

final class MinPklVersionChecker {
  private static final Version currentVersion = Release.current().version();

  // only use major/minor/patch for version check to ease working with dev versions
  private static final Version currentMajorMinorPatchVersion = currentVersion.toNormal();

  static void check(VmTyped module, @Nullable Node importNode) {
    assert module.isModuleObject();

    for (var ann : module.getModuleInfo().getAnnotations()) {
      if (ann.getVmClass() != BaseModule.getModuleInfoClass()) continue;

      // parsing should never fail due to pkl.base#ModuleInfo.minPklVersion's type constraint
      var requiredVersion =
          Version.parse((String) VmUtils.readMember(ann, Identifier.MIN_PKL_VERSION));
      doCheck(module.getModuleInfo().getModuleName(), requiredVersion, importNode);
      return;
    }
  }

  static void check(String moduleName, @Nullable ParserRuleContext ctx, @Nullable Node importNode) {
    if (!(ctx instanceof ModuleContext)) return;

    var moduleDeclCtx = ((ModuleContext) ctx).moduleDecl();
    if (moduleDeclCtx == null) return;

    for (var annCtx : moduleDeclCtx.annotation()) {
      if (!Identifier.MODULE_INFO.toString().equals(getLastIdText(annCtx.type()))) continue;

      var objectBodyCtx = annCtx.objectBody();
      if (objectBodyCtx == null) continue;

      for (var memberCtx : objectBodyCtx.objectMember()) {
        if (!(memberCtx instanceof ObjectPropertyContext)) continue;

        var propertyCtx = (ObjectPropertyContext) memberCtx;
        if (!Identifier.MIN_PKL_VERSION.toString().equals(getText(propertyCtx.Identifier())))
          continue;

        var versionText = getText(propertyCtx.expr());
        if (versionText == null) continue;

        Version version;
        try {
          version = Version.parse(versionText.substring(1, versionText.length() - 1));
        } catch (IllegalArgumentException e) {
          return;
        }

        doCheck(moduleName, version, importNode);
        return;
      }
    }
  }

  private static @Nullable String getText(@Nullable RuleContext ruleCtx) {
    return ruleCtx == null ? null : ruleCtx.getText();
  }

  private static @Nullable String getLastIdText(@Nullable TypeContext typeCtx) {
    if (!(typeCtx instanceof DeclaredTypeContext)) return null;
    var declCtx = (DeclaredTypeContext) typeCtx;
    var token = declCtx.qualifiedIdentifier().Identifier;
    return token == null ? null : token.getText();
  }

  private static @Nullable String getText(@Nullable TerminalNode idCtx) {
    return idCtx == null ? null : idCtx.getText();
  }

  private static void doCheck(
      String moduleName, @Nullable Version requiredVersion, @Nullable Node importNode) {
    if (requiredVersion == null || currentMajorMinorPatchVersion.compareTo(requiredVersion) >= 0)
      return;

    throw new VmExceptionBuilder()
        .withOptionalLocation(importNode)
        .evalError("incompatiblePklVersion", moduleName, requiredVersion, currentVersion)
        .build();
  }
}
