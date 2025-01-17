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
package org.pkl.core.ast.member;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Executed;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.expression.primary.GetReceiverNode;
import org.pkl.core.runtime.VmDynamic;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmListing;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.Nullable;

/** Equivalent of {@link TypedPropertyNode} for elements/entries. */
public abstract class ElementOrEntryNode extends RegularMemberNode {
  @Child @Executed protected ExpressionNode receiverNode = new GetReceiverNode();

  protected ElementOrEntryNode(
      @Nullable VmLanguage language,
      FrameDescriptor descriptor,
      ObjectMember member,
      ExpressionNode bodyNode) {

    super(language, descriptor, member, bodyNode);
  }

  @Specialization
  protected Object evalListing(
      VirtualFrame frame,
      VmListing receiver,
      @Cached("create()") @Shared("callNode") IndirectCallNode callNode) {
    var result = executeBody(frame);
    return VmUtils.shouldRunTypeCheck(frame)
        ? receiver.executeTypeCasts(result, VmUtils.getOwner(frame), callNode, null, null)
        : result;
  }

  @Specialization
  protected Object evalMapping(
      VirtualFrame frame,
      VmMapping receiver,
      @Cached("create()") @Shared("callNode") IndirectCallNode callNode) {
    var result = executeBody(frame);
    return VmUtils.shouldRunTypeCheck(frame)
        ? receiver.executeTypeCasts(result, VmUtils.getOwner(frame), callNode, null, null)
        : result;
  }

  @Specialization
  protected Object evalDynamic(VirtualFrame frame, VmDynamic ignored) {
    return executeBody(frame);
  }
}
