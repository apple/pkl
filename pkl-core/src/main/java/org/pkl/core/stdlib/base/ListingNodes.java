/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.stdlib.base;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import org.pkl.core.ast.PklNode;
import org.pkl.core.ast.expression.binary.EqualNode;
import org.pkl.core.ast.expression.binary.EqualNodeGen;
import org.pkl.core.ast.internal.ReadCursorValueNode;
import org.pkl.core.ast.internal.ToStringNode;
import org.pkl.core.ast.internal.ToStringNodeGen;
import org.pkl.core.ast.lambda.*;
import org.pkl.core.runtime.*;
import org.pkl.core.runtime.VmObjectCursor.CursorOption;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.util.*;

public final class ListingNodes {
  private ListingNodes() {}

  public abstract static class length extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmListing self) {
      return self.getLength();
    }
  }

  public abstract static class isEmpty extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(VmListing self) {
      return self.isEmpty();
    }
  }

  public abstract static class isNotEmpty extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(VmListing self) {
      return !self.isEmpty();
    }
  }

  public abstract static class lastIndex extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmListing self) {
      return self.getLength() - 1;
    }
  }

  public abstract static class getOrNull extends ExternalMethod1Node {
    @Specialization
    protected Object eval(
        VmListing self, long index, @Cached("create()") IndirectCallNode callNode) {
      if (index < 0 || index >= self.getLength()) {
        return VmNull.withoutDefault();
      }
      return VmUtils.readMember(self, index, callNode);
    }
  }

  public abstract static class getOrDefault extends ExternalMethod1Node {
    @Child private IndirectCallNode callNode = IndirectCallNode.create();
    @Child private ApplyVmFunction1Node applyNode = ApplyVmFunction1Node.create();

    @Specialization
    protected Object eval(VmListing self, long index) {
      if (index < 0 || index >= self.getLength()) {
        var defaultFunction = (VmFunction) VmUtils.readMember(self, Identifier.DEFAULT, callNode);
        return applyNode.execute(defaultFunction, index);
      }
      return VmUtils.readMember(self, index, callNode);
    }
  }

  public abstract static class isDistinct extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(
        VirtualFrame frame,
        VmListing self,
        @Cached("create()") ReadCursorValueNode readCursorValueNode) {
      var seenValues = EconomicSets.create();
      for (var cursor = self.elements(CursorOption.ANY_ORDER); cursor.advance(); ) {
        var value = readCursorValueNode.execute(frame, cursor);
        if (!EconomicSets.add(seenValues, value)) {
          return false;
        }
      }
      return true;
    }
  }

  public abstract static class isDistinctBy extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyNode = ApplyVmFunction1Node.create();

    @Specialization
    protected boolean eval(
        VirtualFrame frame,
        VmListing self,
        VmFunction selector,
        @Cached("create()") ReadCursorValueNode readCursorValueNode) {
      var seenValues = EconomicSets.create();
      for (var cursor = self.elements(CursorOption.ANY_ORDER); cursor.advance(); ) {
        var cursorValue = readCursorValueNode.execute(frame, cursor);
        var value = applyNode.execute(selector, cursorValue);
        if (!EconomicSets.add(seenValues, value)) return false;
      }
      return true;
    }
  }

  public abstract static class distinct extends ExternalPropertyNode {
    @Specialization
    protected VmListing eval(
        VirtualFrame frame,
        VmListing self,
        @Cached("create()") ReadCursorValueNode readCursorValueNode) {
      var seenValues = EconomicSets.create();
      var builder = new VmObjectBuilder();
      for (var cursor = self.elements(CursorOption.ALL_VALUES); cursor.advance(); ) {
        var value = readCursorValueNode.execute(frame, cursor);
        if (EconomicSets.add(seenValues, value)) {
          builder.addElement(value);
        }
      }
      return builder.toListing();
    }
  }

  public abstract static class first extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmListing self, @Cached("create()") IndirectCallNode callNode) {
      checkNonEmpty(self, this);
      return VmUtils.readMember(self, 0L, callNode);
    }
  }

  public abstract static class firstOrNull extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmListing self, @Cached("create()") IndirectCallNode callNode) {
      if (self.isEmpty()) {
        return VmNull.withoutDefault();
      }
      return VmUtils.readMember(self, 0L, callNode);
    }
  }

  public abstract static class last extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmListing self, @Cached("create()") IndirectCallNode callNode) {
      checkNonEmpty(self, this);
      return VmUtils.readMember(self, self.getLength() - 1L, callNode);
    }
  }

  public abstract static class lastOrNull extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmListing self, @Cached("create()") IndirectCallNode callNode) {
      var length = self.getLength();
      return length == 0
          ? VmNull.withoutDefault()
          : VmUtils.readMember(self, length - 1L, callNode);
    }
  }

  public abstract static class single extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmListing self, @Cached("create()") IndirectCallNode callNode) {
      checkSingleton(self, this);
      return VmUtils.readMember(self, 0L, callNode);
    }
  }

  public abstract static class singleOrNull extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmListing self, @Cached("create()") IndirectCallNode callNode) {
      if (self.getLength() != 1) {
        return VmNull.withoutDefault();
      }
      return VmUtils.readMember(self, 0L, callNode);
    }
  }

  public abstract static class distinctBy extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyNode = ApplyVmFunction1Node.create();

    @Specialization
    protected VmListing eval(
        VirtualFrame frame,
        VmListing self,
        VmFunction selector,
        @Cached("create()") ReadCursorValueNode readCursorValueNode) {
      var seenValues = EconomicSets.create();
      var builder = new VmObjectBuilder();
      for (var cursor = self.elements(CursorOption.ALL_VALUES); cursor.advance(); ) {
        var value = readCursorValueNode.execute(frame, cursor);
        var selectorValue = applyNode.execute(selector, value);
        if (EconomicSets.add(seenValues, selectorValue)) {
          builder.addElement(value);
        }
      }
      return builder.toListing();
    }
  }

  public abstract static class every extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyNode = ApplyVmFunction1Node.create();

    @Specialization
    protected boolean eval(
        VirtualFrame frame,
        VmListing self,
        VmFunction predicate,
        @Cached("create()") ReadCursorValueNode readCursorValueNode) {
      for (var cursor = self.elements(CursorOption.ANY_ORDER); cursor.advance(); ) {
        var value = readCursorValueNode.execute(frame, cursor);
        if (!applyNode.executeBoolean(predicate, value)) return false;
      }
      return true;
    }
  }

  public abstract static class any extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyNode = ApplyVmFunction1Node.create();

    @Specialization
    protected boolean eval(
        VirtualFrame frame,
        VmListing self,
        VmFunction predicate,
        @Cached("create()") ReadCursorValueNode readCursorValueNode) {
      for (var cursor = self.elements(CursorOption.ANY_ORDER); cursor.advance(); ) {
        var value = readCursorValueNode.execute(frame, cursor);
        if (applyNode.executeBoolean(predicate, value)) return true;
      }
      return false;
    }
  }

  @GenerateWrapper
  public abstract static class contains extends ExternalMethod1Node {
    @Child
    private EqualNode equalNode =
        EqualNodeGen.create(VmUtils.unavailableSourceSection(), true, null, null);

    @Specialization
    protected boolean eval(
        VirtualFrame frame,
        VmListing self,
        Object element,
        @Cached("create()") ReadCursorValueNode readCursorValueNode) {
      for (var cursor = self.elements(CursorOption.ANY_ORDER); cursor.advance(); ) {
        var value = readCursorValueNode.execute(frame, cursor);
        if (equalNode.executeWith(frame, element, value)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probeNode) {
      return new containsWrapper(this, probeNode);
    }
  }

  public abstract static class fold extends ExternalMethod2Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected Object eval(
        VirtualFrame frame,
        VmListing self,
        Object initial,
        VmFunction function,
        @Cached("create()") ReadCursorValueNode readCursorValueNode) {
      var result = initial;
      for (var cursor = self.elements(CursorOption.ALL_VALUES); cursor.advance(); ) {
        var value = readCursorValueNode.execute(frame, cursor);
        result = applyLambdaNode.execute(function, result, value);
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class foldIndexed extends ExternalMethod2Node {
    @Child private ApplyVmFunction3Node applyLambdaNode = ApplyVmFunction3NodeGen.create();

    @Specialization
    protected Object eval(
        VirtualFrame frame,
        VmListing self,
        Object initial,
        VmFunction function,
        @Cached("create()") ReadCursorValueNode readCursorValueNode) {
      var result = initial;
      for (var cursor = self.elements(CursorOption.ALL_VALUES); cursor.advance(); ) {
        var value = readCursorValueNode.execute(frame, cursor);
        result = applyLambdaNode.execute(function, cursor.key(), result, value);
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class join extends ExternalMethod1Node {
    @Child ToStringNode toStringNode = ToStringNodeGen.create(sourceSection, null);

    @Specialization
    protected Object eval(
        VirtualFrame frame,
        VmListing self,
        String separator,
        @Cached("create()") ReadCursorValueNode readCursorValueNode) {
      if (self.isEmpty()) return "";
      var builder = new StringBuilder();
      var isFirst = true;
      for (var cursor = self.elements(); cursor.advance(); ) {
        if (isFirst) {
          isFirst = false;
        } else {
          VmUtils.appendToBuilder(builder, separator);
        }
        var value = toStringNode.executeWith(frame, readCursorValueNode.execute(frame, cursor));
        VmUtils.appendToBuilder(builder, value);
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return VmUtils.builderToString(builder);
    }

    @Override
    public final boolean isInstrumentable() {
      return false;
    }
  }

  public abstract static class toList extends ExternalMethod0Node {
    @Specialization
    protected VmList eval(
        VirtualFrame frame,
        VmListing self,
        @Cached("create()") ReadCursorValueNode readCursorValueNode) {
      var builder = VmList.EMPTY.builder();
      for (var cursor = self.elements(CursorOption.ALL_VALUES); cursor.advance(); ) {
        var value = readCursorValueNode.execute(frame, cursor);
        builder.add(value);
      }
      return builder.build();
    }
  }

  public abstract static class toSet extends ExternalMethod0Node {
    @Specialization
    protected VmSet eval(
        VirtualFrame frame,
        VmListing self,
        @Cached("create()") ReadCursorValueNode readCursorValueNode) {
      var builder = VmSet.EMPTY.builder();
      for (var cursor = self.elements(CursorOption.ALL_VALUES); cursor.advance(); ) {
        var value = readCursorValueNode.execute(frame, cursor);
        builder.add(value);
      }
      return builder.build();
    }
  }

  private static void checkNonEmpty(VmListing self, PklNode node) {
    if (self.isEmpty()) {
      CompilerDirectives.transferToInterpreter();
      throw new VmExceptionBuilder()
          .evalError("expectedNonEmptyListing")
          .withLocation(node)
          .build();
    }
  }

  private static void checkSingleton(VmListing self, PklNode node) {
    if (self.getLength() != 1) {
      CompilerDirectives.transferToInterpreter();
      throw new VmExceptionBuilder()
          .evalError("expectedSingleElementListing")
          .withLocation(node)
          .build();
    }
  }
}
