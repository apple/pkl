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
package org.pkl.core.ast.expression.unary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import java.util.Map;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.GlobResolver.ResolvedGlobElement;

/** Used by {@link ReadGlobNode}. */
public class ReadGlobMemberBodyNode extends ExpressionNode {
  public ReadGlobMemberBodyNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    var mapping = VmUtils.getOwner(frame);
    var path = (String) VmUtils.getMemberKey(frame);
    return readResource(mapping, path);
  }

  private Object readResource(VmObjectLike mapping, String path) {
    @SuppressWarnings("unchecked")
    var globElements = (Map<String, ResolvedGlobElement>) mapping.getExtraStorage();
    var resourceUri = VmUtils.getMapValue(globElements, path).uri();
    var resource = VmContext.get(this).getResourceManager().read(resourceUri, this).orElse(null);
    if (resource == null) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder().evalError("cannotFindResource", resourceUri).build();
    }
    return resource;
  }
}
