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
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import java.io.IOException;
import java.net.URI;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.ast.member.SharedMemberNode;
import org.pkl.core.http.HttpClientInitException;
import org.pkl.core.module.ResolvedModuleKey;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmObjectBuilder;
import org.pkl.core.util.GlobResolver;
import org.pkl.core.util.GlobResolver.InvalidGlobPatternException;
import org.pkl.core.util.LateInit;

@NodeInfo(shortName = "import*")
public class ImportGlobNode extends AbstractImportNode {
  private final String globPattern;
  @Child @LateInit private SharedMemberNode memberNode;
  @CompilationFinal @LateInit private VmMapping cachedResult;

  public ImportGlobNode(
      SourceSection sourceSection,
      ResolvedModuleKey currentModule,
      URI importUri,
      String globPattern) {
    super(sourceSection, currentModule, importUri);
    this.globPattern = globPattern;
  }

  private SharedMemberNode getMemberNode() {
    if (memberNode == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      var language = VmLanguage.get(this);
      memberNode =
          new SharedMemberNode(
              sourceSection,
              sourceSection,
              "",
              language,
              new FrameDescriptor(),
              new ImportGlobMemberBodyNode(sourceSection, language, currentModule));
    }
    return memberNode;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    if (cachedResult != null) return cachedResult;

    CompilerDirectives.transferToInterpreterAndInvalidate();
    var context = VmContext.get(this);
    try {
      var moduleKey = context.getModuleResolver().resolve(importUri);
      if (!moduleKey.isGlobbable()) {
        throw exceptionBuilder()
            .evalError("cannotGlobUri", importUri, importUri.getScheme())
            .build();
      }
      var resolvedElements =
          GlobResolver.resolveGlob(
              context.getSecurityManager(),
              moduleKey,
              currentModule.getOriginal(),
              currentModule.getUri(),
              globPattern);
      var builder = new VmObjectBuilder(resolvedElements.size());
      for (var entry : resolvedElements.entrySet()) {
        builder.addEntry(entry.getKey(), getMemberNode());
      }
      cachedResult = builder.toMapping(resolvedElements);
      return cachedResult;
    } catch (IOException e) {
      throw exceptionBuilder().evalError("ioErrorResolvingGlob", importUri).withCause(e).build();
    } catch (SecurityManagerException | HttpClientInitException e) {
      throw exceptionBuilder().withCause(e).build();
    } catch (PackageLoadError e) {
      throw exceptionBuilder().adhocEvalError(e.getMessage()).build();
    } catch (InvalidGlobPatternException e) {
      throw exceptionBuilder()
          .evalError("invalidGlobPattern", globPattern)
          .withHint(e.getMessage())
          .build();
    }
  }
}
