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
package org.pkl.core.ast.expression.unary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.member.UntypedObjectMemberNode;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.runtime.BaseModule;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.GlobResolver.ResolvedGlobElement;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.LateInit;

@NodeInfo(shortName = "read*")
public abstract class ReadGlobNode extends UnaryExpressionNode {
  private final VmLanguage language;
  private final ModuleKey currentModule;

  @CompilationFinal @LateInit VmMapping readResult;

  protected ReadGlobNode(
      VmLanguage language, SourceSection sourceSection, ModuleKey currentModule) {
    super(sourceSection);
    this.currentModule = currentModule;
    this.language = language;
  }

  @TruffleBoundary
  private URI doResolveUri(String globExpression) {
    try {
      var globUri = IoUtils.toUri(globExpression);
      var tripleDotImport = IoUtils.parseTripleDotPath(globUri);
      if (tripleDotImport != null) {
        throw exceptionBuilder().evalError("cannotGlobTripleDots").build();
      }
      return globUri;
    } catch (URISyntaxException e) {
      throw exceptionBuilder()
          .evalError("invalidResourceUri", globExpression)
          .withHint(e.getReason())
          .build();
    }
  }

  @TruffleBoundary
  private EconomicMap<Object, ObjectMember> buildMembers(
      FrameDescriptor frameDescriptor, List<ResolvedGlobElement> uris) {
    var members = EconomicMaps.<Object, ObjectMember>create();
    for (var entry : uris) {
      var readNode = new StaticReadNode(entry.getUri());
      var member =
          new ObjectMember(
              VmUtils.unavailableSourceSection(),
              VmUtils.unavailableSourceSection(),
              VmModifier.ENTRY,
              null,
              "");
      var memberNode = new UntypedObjectMemberNode(language, frameDescriptor, member, readNode);
      member.initMemberNode(memberNode);
      EconomicMaps.put(members, entry.getPath(), member);
    }
    return members;
  }

  @Specialization
  public Object read(VirtualFrame frame, String globPattern) {
    if (readResult == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      var context = VmContext.get(this);
      var resolvedUri = doResolveUri(globPattern);
      var uris =
          context
              .getResourceManager()
              .resolveGlob(resolvedUri, currentModule.getUri(), currentModule, this, globPattern);
      var members = buildMembers(frame.getFrameDescriptor(), uris);
      readResult =
          new VmMapping(frame.materialize(), BaseModule.getMappingClass().getPrototype(), members);
    }
    return readResult;
  }
}
