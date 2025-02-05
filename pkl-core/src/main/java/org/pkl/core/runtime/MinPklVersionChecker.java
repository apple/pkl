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
package org.pkl.core.runtime;

import com.oracle.truffle.api.nodes.Node;
import org.pkl.core.Release;
import org.pkl.core.Version;
import org.pkl.core.parser.cst.Module;
import org.pkl.core.parser.cst.ObjectMemberNode.ObjectProperty;
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

  static void check(
      String moduleName, @Nullable Module mod, @Nullable Node importNode, String source) {
    if (mod == null) return;

    var moduleDecl = mod.getDecl();
    if (moduleDecl == null) return;

    for (var ann : moduleDecl.getAnnotations()) {
      if (!Identifier.MODULE_INFO.toString().equals(ann.getName().text())) continue;

      var objectBody = ann.getBody();
      if (objectBody == null) continue;

      for (var member : objectBody.getMembers()) {
        if (!(member instanceof ObjectProperty prop)) continue;

        if (!Identifier.MIN_PKL_VERSION.toString().equals(prop.getIdent().getValue())) continue;

        var versionText = prop.getExpr().text(source.toCharArray());

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
