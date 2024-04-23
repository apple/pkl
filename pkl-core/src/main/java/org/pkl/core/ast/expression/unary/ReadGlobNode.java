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
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import java.net.URI;
import java.net.URISyntaxException;
import org.pkl.core.ast.member.SharedMemberNode;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmObjectBuilder;
import org.pkl.core.util.IoUtils;

@NodeInfo(shortName = "read*")
public abstract class ReadGlobNode extends UnaryExpressionNode {
  private final ModuleKey currentModule;
  private final SharedMemberNode readGlobElementNode;

  protected ReadGlobNode(
      VmLanguage language, SourceSection sourceSection, ModuleKey currentModule) {
    super(sourceSection);
    this.currentModule = currentModule;
    readGlobElementNode =
        new SharedMemberNode(
            sourceSection,
            sourceSection,
            "",
            language,
            new FrameDescriptor(),
            new ReadGlobElementNode(sourceSection));
  }

  @Specialization
  @TruffleBoundary
  public Object read(String globPattern) {
    var context = VmContext.get(this);
    var globUri = toUri(globPattern);
    var resolvedElements =
        context
            .getResourceManager()
            .resolveGlob(globUri, currentModule.getUri(), currentModule, this, globPattern);
    var builder = new VmObjectBuilder();
    for (var entry : resolvedElements.entrySet()) {
      builder.addEntry(entry.getKey(), readGlobElementNode);
    }
    return builder.toMapping(resolvedElements);
  }

  private URI toUri(String globPattern) {
    try {
      var globUri = IoUtils.toUri(globPattern);
      if (IoUtils.parseTripleDotPath(globUri) != null) {
        throw exceptionBuilder().evalError("cannotGlobTripleDots").build();
      }
      return globUri;
    } catch (URISyntaxException e) {
      throw exceptionBuilder()
          .evalError("invalidResourceUri", globPattern)
          .withHint(e.getReason())
          .build();
    }
  }
}
