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
package org.pkl.core.ast.member;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.PklRootNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.VmTypeMismatchException;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.util.Nullable;

/** Performs a typecheck on a mapping entry value, or a listing element. */
public class ListingOrMappingTypeCheckNode extends PklRootNode {

  @Child private TypeNode typeNode;
  private final String qualifiedName;

  public ListingOrMappingTypeCheckNode(
      VmLanguage language, FrameDescriptor descriptor, TypeNode typeNode, String qualifiedName) {
    super(language, descriptor);
    this.typeNode = typeNode;
    this.qualifiedName = qualifiedName;
  }

  @Override
  public SourceSection getSourceSection() {
    return typeNode.getSourceSection();
  }

  @Override
  public @Nullable String getName() {
    return qualifiedName;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    try {
      return typeNode.execute(frame, frame.getArguments()[2]);
    } catch (VmTypeMismatchException e) {
      CompilerDirectives.transferToInterpreter();
      throw e.toVmException();
    }
  }
}
