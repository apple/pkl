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
package org.pkl.core.ast.expression.unary;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import java.util.Map;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.http.HttpClientInitException;
import org.pkl.core.module.ResolvedModuleKey;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.GlobResolver.ResolvedGlobElement;

/** Used by {@link ReadGlobNode}. */
public final class ImportGlobMemberBodyNode extends ExpressionNode {
  private final VmLanguage language;
  private final ResolvedModuleKey currentModule;

  public ImportGlobMemberBodyNode(
      SourceSection sourceSection, VmLanguage language, ResolvedModuleKey currentModule) {
    super(sourceSection);
    this.language = language;
    this.currentModule = currentModule;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    var mapping = VmUtils.getObjectReceiver(frame);
    var path = (String) VmUtils.getMemberKey(frame);
    return importModule(mapping, path);
  }

  @TruffleBoundary
  private VmTyped importModule(VmObjectLike mapping, String path) {
    @SuppressWarnings("unchecked")
    var globElements = (Map<String, ResolvedGlobElement>) mapping.getExtraStorage();
    var importUri = globElements.get(path).getUri();
    var context = VmContext.get(this);
    try {
      context.getSecurityManager().checkImportModule(currentModule.getUri(), importUri);
      var moduleToImport = context.getModuleResolver().resolve(importUri, this);
      return language.loadModule(moduleToImport, this);
    } catch (SecurityManagerException | PackageLoadError | HttpClientInitException e) {
      throw exceptionBuilder().withCause(e).build();
    }
  }
}
