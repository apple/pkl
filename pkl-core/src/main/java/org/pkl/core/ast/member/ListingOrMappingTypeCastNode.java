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
package org.pkl.core.ast.member;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.PklRootNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.VmTypeMismatchException;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.Nullable;

/** Performs a typecast on a Mapping entry value, or a Listing element. */
public class ListingOrMappingTypeCastNode extends PklRootNode {

  @Child private TypeNode typeNode;
  private final String qualifiedName;
  private final @Nullable VmObjectLike owner;
  private final @Nullable Object receiver;

  public ListingOrMappingTypeCastNode(
      VmLanguage language,
      FrameDescriptor descriptor,
      TypeNode typeNode,
      String qualifiedName,
      @Nullable VirtualFrame originalFrame) {
    super(language, descriptor);
    this.typeNode = typeNode;
    this.qualifiedName = qualifiedName;
    if (originalFrame != null) {
      owner = VmUtils.getOwner(originalFrame);
      receiver = VmUtils.getReceiver(originalFrame);
    } else {
      owner = null;
      receiver = null;
    }
  }

  public TypeNode getTypeNode() {
    return typeNode;
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
    if (owner == null || receiver == null) return executeInternal(frame);
    var prevOwner = VmUtils.getOwner(frame);
    var prevReceiver = VmUtils.getReceiver(frame);
    try {
      VmUtils.setOwner(frame, owner);
      VmUtils.setReceiver(frame, receiver);
      return executeInternal(frame);
    } finally {
      VmUtils.setOwner(frame, prevOwner);
      VmUtils.setReceiver(frame, prevReceiver);
    }
  }

  private Object executeInternal(VirtualFrame frame) {
    try {
      return typeNode.execute(frame, frame.getArguments()[2]);
    } catch (VmTypeMismatchException e) {
      CompilerDirectives.transferToInterpreter();
      throw e.toVmException();
    }
  }
}
