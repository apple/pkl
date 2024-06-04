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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import java.net.URI;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.http.HttpClientInitException;
import org.pkl.core.module.ResolvedModuleKey;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.util.LateInit;

@NodeInfo(shortName = "import")
public final class ImportNode extends AbstractImportNode {
  private final VmLanguage language;

  @CompilationFinal @LateInit private VmTyped importedModule;

  public ImportNode(
      VmLanguage language,
      SourceSection sourceSection,
      ResolvedModuleKey currentModule,
      URI importUri) {
    super(sourceSection, currentModule, importUri);
    this.language = language;

    assert importUri.isAbsolute();
  }

  public Object executeGeneric(VirtualFrame frame) {
    if (importedModule == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      var context = VmContext.get(this);
      try {
        context.getSecurityManager().checkImportModule(currentModule.getUri(), importUri);
        var moduleToImport = context.getModuleResolver().resolve(importUri, this);
        importedModule = language.loadModule(moduleToImport, this);
      } catch (SecurityManagerException | PackageLoadError | HttpClientInitException e) {
        throw exceptionBuilder().withCause(e).build();
      }
    }

    return importedModule;
  }
}
