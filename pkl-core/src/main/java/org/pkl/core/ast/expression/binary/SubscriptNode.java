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
package org.pkl.core.ast.expression.binary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.runtime.*;

@NodeInfo(shortName = "[]")
public abstract class SubscriptNode extends BinaryExpressionNode {
  protected SubscriptNode(SourceSection sourceSection) {
    super(sourceSection);
  }

  @Specialization
  @TruffleBoundary
  protected String eval(String receiver, long index) {
    var charIndex = VmUtils.codePointOffsetToCharOffset(receiver, index);
    if (charIndex == -1 || charIndex == receiver.length()) {
      throw exceptionBuilder()
          .evalError(
              "charIndexOutOfRange", index, 0, receiver.codePointCount(0, receiver.length()) - 1)
          .withSourceSection(getRightNode().getSourceSection())
          .withProgramValue("String", receiver)
          .build();
    }

    if (Character.isHighSurrogate(receiver.charAt(charIndex))) {
      return receiver.substring(charIndex, charIndex + 2);
    }
    return receiver.substring(charIndex, charIndex + 1);
  }

  @Specialization
  protected Object eval(VmList receiver, long index) {
    if (index < 0 || index >= receiver.getLength()) {
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("elementIndexOutOfRange", index, 0, receiver.getLength() - 1)
          .withProgramValue("Collection", receiver)
          .build();
    }
    return receiver.get(index);
  }

  @Specialization
  protected Object eval(VmMap receiver, Object key) {
    var result = receiver.getOrNull(key);
    if (result != null) return result;

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder().cannotFindKey(receiver, key).build();
  }

  @Specialization
  protected Object eval(
      VirtualFrame frame,
      VmListing listing,
      long index,
      @Exclusive @Cached("create()") IndirectCallNode callNode) {

    var result = VmUtils.readMemberOrNull(listing, VmUtils.getMarkers(frame), index, callNode);
    if (result != null) return result;

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder()
        .evalError("elementIndexOutOfRange", index, 0, listing.getLength() - 1)
        .build();
  }

  @Specialization
  protected Object eval(
      VirtualFrame frame,
      VmMapping mapping,
      Object key,
      @Exclusive @Cached("create()") IndirectCallNode callNode) {

    return readMember(frame, mapping, key, callNode);
  }

  @Specialization
  protected Object eval(
      VirtualFrame frame,
      VmDynamic dynamic,
      Object key,
      @Exclusive @Cached("create()") IndirectCallNode callNode) {

    return readMember(frame, dynamic, key, callNode);
  }

  private Object readMember(
      VirtualFrame frame, VmObject object, Object key, IndirectCallNode callNode) {
    var result = VmUtils.readMemberOrNull(object, VmUtils.getMarkers(frame), key, callNode);
    if (result != null) return result;

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder().cannotFindMember(object, key).build();
  }
}
