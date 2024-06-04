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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import java.io.IOException;
import java.net.URISyntaxException;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.ast.member.SharedMemberNode;
import org.pkl.core.http.HttpClientInitException;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmObjectBuilder;
import org.pkl.core.util.GlobResolver;
import org.pkl.core.util.GlobResolver.InvalidGlobPatternException;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.LateInit;

@NodeInfo(shortName = "read*")
public abstract class ReadGlobNode extends AbstractReadNode {
  private final EconomicMap<String, VmMapping> cachedResults = EconomicMap.create();
  @Child @LateInit private SharedMemberNode memberNode;

  protected ReadGlobNode(SourceSection sourceSection, ModuleKey currentModule) {
    super(sourceSection, currentModule);
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
              new ReadGlobMemberBodyNode(sourceSection));
    }
    return memberNode;
  }

  @Specialization
  @TruffleBoundary
  public Object read(String globPattern) {
    var cachedResult = cachedResults.get(globPattern);
    if (cachedResult != null) return cachedResult;

    // use same check as for globbed imports (see AstBuilder)
    if (globPattern.startsWith("...")) {
      throw exceptionBuilder().evalError("cannotGlobTripleDots").build();
    }
    var globUri = parseUri(globPattern);
    var context = VmContext.get(this);
    try {
      var resolvedUri = IoUtils.resolve(context.getSecurityManager(), currentModule, globUri);
      var reader = context.getResourceManager().getReader(resolvedUri, this);
      if (!reader.isGlobbable()) {
        throw exceptionBuilder().evalError("cannotGlobUri", globUri, globUri.getScheme()).build();
      }
      var resolvedElements =
          GlobResolver.resolveGlob(
              context.getSecurityManager(),
              reader,
              currentModule,
              currentModule.getUri(),
              globPattern);
      var builder = new VmObjectBuilder(resolvedElements.size());
      for (var entry : resolvedElements.entrySet()) {
        builder.addEntry(entry.getKey(), getMemberNode());
      }
      cachedResult = builder.toMapping(resolvedElements);
      cachedResults.put(globPattern, cachedResult);
      return cachedResult;
    } catch (IOException e) {
      throw exceptionBuilder().evalError("ioErrorResolvingGlob", globPattern).withCause(e).build();
    } catch (SecurityManagerException | HttpClientInitException | URISyntaxException e) {
      throw exceptionBuilder().withCause(e).build();
    } catch (InvalidGlobPatternException e) {
      throw exceptionBuilder()
          .evalError("invalidGlobPattern", globPattern)
          .withHint(e.getMessage())
          .build();
    }
  }
}
