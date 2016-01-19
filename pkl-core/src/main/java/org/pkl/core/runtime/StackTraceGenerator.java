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
package org.pkl.core.runtime;

import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import java.util.*;
import org.pkl.core.StackFrame;
import org.pkl.core.ast.MemberNode;
import org.pkl.core.util.Nullable;

class StackTraceGenerator {
  private final VmException exception;

  private final List<StackFrame> frames = new ArrayList<>();

  static List<StackFrame> capture(VmException exception) {
    return new StackTraceGenerator(exception).capture();
  }

  private StackTraceGenerator(VmException exception) {
    this.exception = exception;
  }

  private List<StackFrame> capture() {
    // When dealing with stack overflows, the actual exception containing the Truffle frames is
    // on the StackOverflowError and not the parent VmException.
    var exp = exception.getCause() instanceof StackOverflowError ? exception.getCause() : exception;
    var truffleElements = TruffleStackTrace.getStackTrace(exp);
    if (truffleElements.isEmpty()) {
      addFrame(exception.getSourceSection(), exception.getMemberName());
    } else {
      var isFirst = true; // copy before mutating to be on the safe side
      var insertedStackFrames = new HashMap<>(exception.getInsertedStackFrames());
      for (var element : truffleElements) {
        var callNode = element.getLocation();
        addFrame(findDisplayableSourceSection(callNode, isFirst), getMemberName(element));
        isFirst = false;

        var callTarget = element.getTarget();
        var insertedFrame = insertedStackFrames.remove(callTarget);
        if (insertedFrame != null) frames.add(insertedFrame);
      }
    }

    return frames;
  }

  // customization of Node.getEncapsulatingSourceSection()
  private SourceSection findDisplayableSourceSection(@Nullable Node callNode, boolean isFirst) {
    if (isFirst && exception.getSourceSection() != null) {
      return exception.getSourceSection();
    }

    for (Node current = callNode; current != null; current = current.getParent()) {
      if (current.getSourceSection() != null) {
        return current instanceof MemberNode
            // Always display the member body's source section instead of the member
            // (root) node's source section (which includes doc comment etc.), even
            // if `callNode` is a child of root node rather than body node.
            // This improves stack trace output for failed property type checks.
            ? ((MemberNode) current).getBodySection()
            : current.getSourceSection();
      }
    }

    return VmUtils.unavailableSourceSection();
  }

  private void addFrame(@Nullable SourceSection section, @Nullable String memberName) {
    if (section == null || !section.isAvailable()) {
      // no point in displaying this frame.
      // a legitimate case where we end up here is a default property
      // value failing its property type check (e.g. List(!isEmpty)).
      // in that case, unless we want to display pseudo code for the implicit
      // default value, there is nothing better than skipping the frame.
      return;
    }

    frames.add(VmUtils.createStackFrame(section, memberName));
  }

  private @Nullable String getMemberName(@Nullable TruffleStackTraceElement element) {
    if (element == null) return null;

    var rootNode = element.getTarget().getRootNode();
    if (rootNode == null) return null;

    return rootNode.getName();
  }
}
