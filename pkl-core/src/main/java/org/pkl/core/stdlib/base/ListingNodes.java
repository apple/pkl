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
package org.pkl.core.stdlib.base;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.LoopNode;
import org.pkl.core.ast.lambda.*;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.*;
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

  public abstract static class lastIndex extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmListing self) {
      return self.getLength() - 1;
    }
  }

  public abstract static class getOrNull extends ExternalMethod1Node {
    @Specialization
    protected Object eval(VmListing self, long index) {
      if (index < 0 || index >= self.getLength()) {
        return VmNull.withoutDefault();
      }
      return VmUtils.readMember(self, index);
    }
  }

  public abstract static class isDistinct extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected boolean eval(VmListing self) {
      var visitedValues = CollectionUtils.newHashSet();
      return self.forceAndIterateMemberValues((key, member, value) -> visitedValues.add(value));
    }
  }

  public abstract static class isDistinctBy extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyNode = ApplyVmFunction1Node.create();

    @Specialization
    protected boolean eval(VmListing self, VmFunction selector) {
      var visitedValues = CollectionUtils.newHashSet();
      return self.forceAndIterateMemberValues(
          (key, member, value) -> visitedValues.add(applyNode.execute(selector, value)));
    }
  }

  public abstract static class distinct extends ExternalPropertyNode {
    @Specialization
    protected VmListing eval(VmListing self) {
      var visitedValues = CollectionUtils.newHashSet();
      var newMembers = EconomicMaps.<Object, ObjectMember>create();
      var index = new MutableLong(0);

      self.forceAndIterateMemberValues(
          (key, member, value) -> {
            if (visitedValues.add(value)) {
              newMembers.put(
                  index.getAndIncrement(),
                  VmUtils.createSyntheticObjectElement(member.getQualifiedName(), value));
            }
            return true;
          });

      return new VmListing(
          VmUtils.createEmptyMaterializedFrame(),
          BaseModule.getListingClass().getPrototype(),
          newMembers,
          newMembers.size());
    }
  }

  public abstract static class first extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmListing self) {
      checkNonEmpty(self);
      return VmUtils.readMember(self, 0L);
    }
  }

  public abstract static class firstOrNull extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmListing self) {
      if (self.isEmpty()) {
        return VmNull.withoutDefault();
      }
      return VmUtils.readMember(self, 0L);
    }
  }

  public abstract static class last extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmListing self) {
      checkNonEmpty(self);
      return VmUtils.readMember(self, self.getLength() - 1L);
    }
  }

  public abstract static class lastOrNull extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmListing self) {
      var length = self.getLength();
      return length == 0 ? VmNull.withoutDefault() : VmUtils.readMember(self, length - 1L);
    }
  }

  public abstract static class single extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmListing self) {
      checkSingleton(self);
      return VmUtils.readMember(self, 0L);
    }
  }

  public abstract static class singleOrNull extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmListing self) {
      if (self.getLength() != 1) {
        return VmNull.withoutDefault();
      }
      return VmUtils.readMember(self, 0L);
    }
  }

  public abstract static class distinctBy extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyNode = ApplyVmFunction1Node.create();

    @Specialization
    protected VmListing eval(VmListing self, VmFunction selector) {
      var visitedValues = CollectionUtils.newHashSet();
      var newMembers = EconomicMaps.<Object, ObjectMember>create();
      var index = new MutableLong(0);

      self.forceAndIterateMemberValues(
          (key, member, value) -> {
            if (visitedValues.add(applyNode.execute(selector, value))) {
              newMembers.put(
                  index.getAndIncrement(),
                  VmUtils.createSyntheticObjectElement(member.getQualifiedName(), value));
            }
            return true;
          });

      return new VmListing(
          VmUtils.createEmptyMaterializedFrame(),
          BaseModule.getListingClass().getPrototype(),
          newMembers,
          newMembers.size());
    }
  }

  public abstract static class fold extends ExternalMethod2Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected Object eval(VmListing self, Object initial, VmFunction function) {
      var result = new MutableReference<>(initial);
      self.forceAndIterateMemberValues(
          (key, member, value) -> {
            result.set(applyLambdaNode.execute(function, result.get(), value));
            return true;
          });
      LoopNode.reportLoopCount(this, self.getLength());
      return result.get();
    }
  }

  public abstract static class foldIndexed extends ExternalMethod2Node {
    @Child private ApplyVmFunction3Node applyLambdaNode = ApplyVmFunction3NodeGen.create();

    @Specialization
    protected Object eval(VmListing self, Object initial, VmFunction function) {
      var index = new MutableLong(0);
      var result = new MutableReference<>(initial);
      self.forceAndIterateMemberValues(
          (key, member, value) -> {
            result.set(
                applyLambdaNode.execute(function, index.getAndIncrement(), result.get(), value));
            return true;
          });
      LoopNode.reportLoopCount(this, self.getLength());
      return result.get();
    }
  }

  public abstract static class join extends ExternalMethod1Node {
    @Specialization
    protected Object eval(VmListing self, String separator) {
      if (self.isEmpty()) return "";

      var builder = new StringBuilder();
      self.forceAndIterateMemberValues(
          (key, member, value) -> {
            if (!key.equals(0L)) {
              builder.append(separator);
            }
            builder.append(value);
            return true;
          });
      LoopNode.reportLoopCount(this, self.getLength());
      return builder.toString();
    }
  }

  public abstract static class toList extends ExternalMethod0Node {
    @Specialization
    protected VmList eval(VmListing self) {
      var builder = VmList.EMPTY.builder();
      self.forceAndIterateMemberValues(
          (key, member, value) -> {
            builder.add(value);
            return true;
          });
      return builder.build();
    }
  }

  public abstract static class toSet extends ExternalMethod0Node {
    @Specialization
    protected VmSet eval(VmListing self) {
      var builder = VmSet.EMPTY.builder();
      self.forceAndIterateMemberValues(
          (key, member, value) -> {
            builder.add(value);
            return true;
          });
      return builder.build();
    }
  }

  @TruffleBoundary
  private static void checkNonEmpty(VmListing self) {
    if (self.isEmpty()) {
      CompilerDirectives.transferToInterpreter();
      throw new VmExceptionBuilder().evalError("expectedNonEmptyListing").build();
    }
  }

  @TruffleBoundary
  private static void checkSingleton(VmListing self) {
    if (self.getLength() != 1) {
      CompilerDirectives.transferToInterpreter();
      throw new VmExceptionBuilder().evalError("expectedSingleElementListing").build();
    }
  }
}
