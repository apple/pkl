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

import com.oracle.truffle.api.frame.VirtualFrame;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.runtime.VmUtils;

/**
 * Delegates to the equally named member of the object stored in extra storage. If no such member
 * exists, delegates to its own parent member.
 */
public final class DelegateToExtraStorageObjOrParentNode extends ExpressionNode {
  @Override
  public Object executeGeneric(VirtualFrame frame) {
    var receiver = (VmObjectLike) VmUtils.getReceiver(frame);
    var owner = VmUtils.getOwner(frame);
    var delegate = (VmObjectLike) owner.getExtraStorage();
    var memberKey = VmUtils.getMemberKey(frame);
    var result = VmUtils.readMemberOrNull(delegate, memberKey);
    if (result != null) return result;
    var parent = owner.getParent();
    assert parent != null;
    return VmUtils.readMember(receiver, parent, memberKey);
  }
}
