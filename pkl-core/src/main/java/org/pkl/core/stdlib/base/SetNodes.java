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
import org.graalvm.collections.EconomicMap;
import org.pkl.core.ast.expression.binary.GreaterThanNode;
import org.pkl.core.ast.expression.binary.GreaterThanNodeGen;
import org.pkl.core.ast.expression.binary.LessThanNode;
import org.pkl.core.ast.expression.binary.LessThanNodeGen;
import org.pkl.core.ast.internal.IsInstanceOfNode;
import org.pkl.core.ast.internal.IsInstanceOfNodeGen;
import org.pkl.core.ast.lambda.*;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.*;
import org.pkl.core.stdlib.base.CollectionNodes.CompareByNode;
import org.pkl.core.stdlib.base.CollectionNodes.CompareNode;
import org.pkl.core.stdlib.base.CollectionNodes.CompareWithNode;
import org.pkl.core.util.EconomicMaps;

// duplication between ListNodes and SetNodes is "intentional"
// (sharing nodes between VmCollection subtypes results in
// polymorphic guards/calls which seems less Truffle idiomatic)
@SuppressWarnings("Duplicates")
public final class SetNodes {
  private SetNodes() {}

  public abstract static class length extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmSet self) {
      return self.getLength();
    }
  }

  public abstract static class isEmpty extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(VmSet self) {
      return self.isEmpty();
    }
  }

  public abstract static class first extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmSet self) {
      return self.getFirst();
    }
  }

  public abstract static class firstOrNull extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmSet self) {
      return self.getFirstOrNull();
    }
  }

  public abstract static class rest extends ExternalPropertyNode {
    @Specialization
    protected VmSet eval(VmSet self) {
      return self.getRest();
    }
  }

  public abstract static class restOrNull extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmSet self) {
      return self.getRestOrNull();
    }
  }

  public abstract static class last extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmSet self) {
      return self.getLast();
    }
  }

  public abstract static class lastOrNull extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmSet self) {
      return self.getLastOrNull();
    }
  }

  public abstract static class single extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmSet self) {
      return self.getSingle();
    }
  }

  public abstract static class singleOrNull extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmSet self) {
      return self.getSingleOrNull();
    }
  }

  public abstract static class startsWith extends ExternalMethod1Node {
    @Specialization
    protected boolean eval(VmSet self, VmCollection other) {
      return self.startsWith(other);
    }
  }

  public abstract static class endsWith extends ExternalMethod1Node {
    @Specialization
    protected boolean eval(VmSet self, VmCollection other) {
      return self.endsWith(other);
    }
  }

  public abstract static class split extends ExternalMethod1Node {
    @Specialization
    protected VmPair eval(VmSet self, long index) {
      if (index < 0 || index > self.getLength()) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder()
            .evalError("elementIndexOutOfRange", index, 0, self.getLength())
            .withProgramValue("Collection", self)
            .build();
      }
      return self.split(index);
    }
  }

  public abstract static class splitOrNull extends ExternalMethod1Node {
    @Specialization
    protected Object eval(VmSet self, long index) {
      return self.splitOrNull(index);
    }
  }

  public abstract static class partition extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    @Specialization
    protected VmPair eval(VmSet self, VmFunction function) {
      var builder1 = self.builder();
      var builder2 = self.builder();
      for (var elem : self) {
        if (applyLambdaNode.executeBoolean(function, elem)) {
          builder1.add(elem);
        } else {
          builder2.add(elem);
        }
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return new VmPair(builder1.build(), builder2.build());
    }
  }

  public abstract static class contains extends ExternalMethod1Node {
    @Specialization
    protected boolean eval(VmSet self, Object element) {
      return self.contains(element);
    }
  }

  public abstract static class find extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    @Specialization
    protected Object eval(VmSet self, VmFunction function) {
      for (var elem : self) {
        if (applyLambdaNode.executeBoolean(function, elem)) return elem;
      }

      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("cannotFindMatchingCollectionElement")
          .withProgramValue("Collection", self)
          .build();
    }
  }

  public abstract static class findOrNull extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    @Specialization
    protected Object eval(VmSet self, VmFunction function) {
      for (var elem : self) {
        if (applyLambdaNode.executeBoolean(function, elem)) return elem;
      }
      return VmNull.withoutDefault();
    }
  }

  public abstract static class findLast extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    @Specialization
    protected Object eval(VmSet self, VmFunction function) {
      var iterator = self.reverseIterator();
      while (iterator.hasNext()) {
        var elem = iterator.next();
        if (applyLambdaNode.executeBoolean(function, elem)) return elem;
      }

      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .evalError("cannotFindMatchingCollectionElement")
          .withProgramValue("Collection", self)
          .build();
    }
  }

  public abstract static class findLastOrNull extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    @Specialization
    protected Object eval(VmSet self, VmFunction function) {
      var iterator = self.reverseIterator();
      while (iterator.hasNext()) {
        var elem = iterator.next();
        if (applyLambdaNode.executeBoolean(function, elem)) return elem;
      }
      return VmNull.withoutDefault();
    }
  }

  public abstract static class every extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    @Specialization
    protected boolean eval(VmSet self, VmFunction function) {
      for (var elem : self) {
        if (!applyLambdaNode.executeBoolean(function, elem)) return false;
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return true;
    }
  }

  public abstract static class any extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    @Specialization
    protected boolean eval(VmSet self, VmFunction function) {
      for (var elem : self) {
        if (applyLambdaNode.executeBoolean(function, elem)) return true;
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return false;
    }
  }

  public abstract static class filter extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    @Specialization
    protected VmSet eval(VmSet self, VmFunction function) {
      var builder = self.builder();
      for (var elem : self) {
        if (applyLambdaNode.executeBoolean(function, elem)) {
          builder.add(elem);
        }
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class filterNonNull extends ExternalMethod0Node {
    @Specialization
    protected VmSet eval(VmSet self) {
      var builder = self.builder();
      for (var elem : self) {
        if (elem instanceof VmNull) continue;
        builder.add(elem);
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class filterIndexed extends ExternalMethod1Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected VmSet eval(VmSet self, VmFunction function) {
      var builder = self.builder();
      long index = 0;

      for (var elem : self) {
        if (applyLambdaNode.executeBoolean(function, index++, elem)) {
          builder.add(elem);
        }
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class filterIsInstance extends ExternalMethod1Node {
    @Child private IsInstanceOfNode isInstanceOfNode = IsInstanceOfNodeGen.create();

    @Specialization
    protected VmSet eval(VmSet self, VmClass clazz) {
      var builder = self.builder();
      for (var elem : self) {
        if (isInstanceOfNode.executeBoolean(elem, clazz)) {
          builder.add(elem);
        }
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class map extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    @Specialization
    protected VmSet eval(VmSet self, VmFunction function) {
      var builder = self.builder();
      for (var elem : self) {
        builder.add(applyLambdaNode.execute(function, elem));
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class mapNonNull extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    @Specialization
    protected VmSet eval(VmSet self, VmFunction function) {
      var builder = self.builder();
      for (var elem : self) {
        var newValue = applyLambdaNode.execute(function, elem);
        if (newValue instanceof VmNull) continue;
        builder.add(newValue);
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class mapIndexed extends ExternalMethod1Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected VmSet eval(VmSet self, VmFunction function) {
      var builder = self.builder();
      long index = 0;

      for (var elem : self) {
        builder.add(applyLambdaNode.execute(function, index++, elem));
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class flatMap extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    @Specialization
    protected VmSet eval(VmSet self, VmFunction function) {
      var builder = self.builder();
      for (var elem : self) {
        builder.addAll(applyLambdaNode.executeCollection(function, elem));
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class flatMapIndexed extends ExternalMethod1Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected VmSet eval(VmSet self, VmFunction function) {
      var builder = self.builder();
      long index = 0;

      for (var elem : self) {
        builder.addAll(applyLambdaNode.executeCollection(function, index++, elem));
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class flatten extends ExternalMethod0Node {
    @Specialization
    protected VmSet eval(VmSet self) {
      return (VmSet) self.flatten();
    }
  }

  public abstract static class take extends ExternalMethod1Node {
    @Specialization
    protected VmSet eval(VmSet self, long n) {
      return self.take(n);
    }
  }

  public abstract static class takeWhile extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    @Specialization
    protected VmSet eval(VmSet self, VmFunction function) {
      var builder = self.builder();
      for (var elem : self) {
        if (!applyLambdaNode.executeBoolean(function, elem)) {
          return builder.build();
        }
        builder.add(elem);
      }
      return self;
    }
  }

  public abstract static class takeLast extends ExternalMethod1Node {
    @Specialization
    protected VmSet eval(VmSet self, long n) {
      return self.takeLast(n);
    }
  }

  public abstract static class takeLastWhile extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    @Specialization
    protected VmSet eval(VmSet self, VmFunction function) {
      var idx = self.getLength();
      var iter = self.reverseIterator();
      while (iter.hasNext()) {
        if (!applyLambdaNode.executeBoolean(function, iter.next())) break;
        idx -= 1;
      }
      return self.drop(idx);
    }
  }

  public abstract static class drop extends ExternalMethod1Node {
    @Specialization
    protected VmSet eval(VmSet self, long n) {
      return self.drop(n);
    }
  }

  public abstract static class dropWhile extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    @Specialization
    protected VmSet eval(VmSet self, VmFunction function) {
      var idx = 0;
      for (var elem : self) {
        if (!applyLambdaNode.executeBoolean(function, elem)) break;
        idx += 1;
      }
      return self.drop(idx);
    }
  }

  public abstract static class dropLast extends ExternalMethod1Node {
    @Specialization
    protected VmSet eval(VmSet self, long n) {
      return self.dropLast(n);
    }
  }

  public abstract static class dropLastWhile extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    @Specialization
    protected VmSet eval(VmSet self, VmFunction function) {
      var idx = self.getLength();
      var iter = self.reverseIterator();
      while (iter.hasNext()) {
        if (!applyLambdaNode.executeBoolean(function, iter.next())) break;
        idx -= 1;
      }
      return self.take(idx);
    }
  }

  public abstract static class fold extends ExternalMethod2Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected Object eval(VmSet self, Object initial, VmFunction function) {
      var iter = self.iterator();
      var result = initial;
      while (iter.hasNext()) {
        var elem = iter.next();
        result = applyLambdaNode.execute(function, result, elem);
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class foldBack extends ExternalMethod2Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected Object eval(VmSet self, Object initial, VmFunction function) {
      var iter = self.reverseIterator();
      var result = initial;
      while (iter.hasNext()) {
        var elem = iter.next();
        result = applyLambdaNode.execute(function, elem, result);
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class foldIndexed extends ExternalMethod2Node {
    @Child private ApplyVmFunction3Node applyLambdaNode = ApplyVmFunction3NodeGen.create();

    @Specialization
    protected Object eval(VmSet self, Object initial, VmFunction function) {
      var iter = self.iterator();
      var result = initial;
      long index = 0;

      while (iter.hasNext()) {
        var elem = iter.next();
        result = applyLambdaNode.execute(function, index++, result, elem);
      }

      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class reduce extends ExternalMethod1Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected Object eval(VmSet self, VmFunction function) {
      self.checkNonEmpty();

      var iterator = self.iterator();
      var result = iterator.next();
      while (iterator.hasNext()) {
        var elem = iterator.next();
        result = applyLambdaNode.execute(function, result, elem);
      }

      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class reduceOrNull extends ExternalMethod1Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected Object eval(VmSet self, VmFunction function) {
      if (self.isEmpty()) return VmNull.withoutDefault();

      var iterator = self.iterator();
      var result = iterator.next();
      while (iterator.hasNext()) {
        var elem = iterator.next();
        result = applyLambdaNode.execute(function, result, elem);
      }

      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class groupBy extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1NodeGen.create();

    @Specialization
    protected Object eval(VmSet self, VmFunction function) {
      var builder = VmMap.builder();

      for (Object elem : self) {
        var key = applyLambdaNode.execute(function, elem);
        var value = builder.get(key);
        var newValue = value == null ? VmSet.of(elem) : ((VmSet) value).add(elem);
        builder.add(key, newValue);
      }

      LoopNode.reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class min extends ExternalPropertyNode {
    @Child
    @SuppressWarnings("ConstantConditions")
    private LessThanNode lessThanNode =
        LessThanNodeGen.create(VmUtils.unavailableSourceSection(), null, null);

    @Specialization
    protected Object eval(VmSet self) {
      self.checkNonEmpty();

      var iterator = self.iterator();
      var result = iterator.next();

      while (iterator.hasNext()) {
        var elem = iterator.next();
        if (lessThanNode.executeWith(elem, result)) {
          result = elem;
        }
      }

      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class minOrNull extends ExternalPropertyNode {
    @Child
    @SuppressWarnings("ConstantConditions")
    private LessThanNode lessThanNode =
        LessThanNodeGen.create(VmUtils.unavailableSourceSection(), null, null);

    @Specialization
    protected Object eval(VmSet self) {
      if (self.isEmpty()) return VmNull.withoutDefault();

      var iterator = self.iterator();
      var result = iterator.next();

      while (iterator.hasNext()) {
        var elem = iterator.next();
        if (lessThanNode.executeWith(elem, result)) {
          result = elem;
        }
      }

      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class minBy extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1NodeGen.create();

    @Child
    @SuppressWarnings("ConstantConditions")
    private LessThanNode lessThanNode =
        LessThanNodeGen.create(VmUtils.unavailableSourceSection(), null, null);

    @Specialization
    protected Object eval(VmSet self, VmFunction function) {
      self.checkNonEmpty();

      var iterator = self.iterator();
      var result = iterator.next();
      var resultValue = applyLambdaNode.execute(function, result);

      while (iterator.hasNext()) {
        var elem = iterator.next();
        var elemValue = applyLambdaNode.execute(function, elem);
        if (lessThanNode.executeWith(elemValue, resultValue)) {
          result = elem;
          resultValue = elemValue;
        }
      }

      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class minByOrNull extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1NodeGen.create();

    @Child
    @SuppressWarnings("ConstantConditions")
    private LessThanNode lessThanNode =
        LessThanNodeGen.create(VmUtils.unavailableSourceSection(), null, null);

    @Specialization
    protected Object eval(VmSet self, VmFunction function) {
      if (self.isEmpty()) return VmNull.withoutDefault();

      var iterator = self.iterator();
      var result = iterator.next();
      var resultValue = applyLambdaNode.execute(function, result);

      while (iterator.hasNext()) {
        var elem = iterator.next();
        var elemValue = applyLambdaNode.execute(function, elem);
        if (lessThanNode.executeWith(elemValue, resultValue)) {
          result = elem;
          resultValue = elemValue;
        }
      }

      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class minWith extends ExternalMethod1Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected Object eval(VmSet self, VmFunction function) {
      self.checkNonEmpty();

      var iterator = self.iterator();
      var result = iterator.next();
      while (iterator.hasNext()) {
        var elem = iterator.next();
        var cmpResult = applyLambdaNode.execute(function, elem, result);
        if (cmpResult instanceof Boolean b) {
          if (b) result = elem;
        } else if (cmpResult instanceof Long l) { // deprecated
          if (l < 0) result = elem;
        } else {
          CompilerDirectives.transferToInterpreter();
          throw exceptionBuilder()
              .typeMismatch(cmpResult, BaseModule.getBooleanClass(), BaseModule.getIntClass())
              .build();
        }
      }

      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class minWithOrNull extends ExternalMethod1Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected Object eval(VmSet self, VmFunction function) {
      if (self.isEmpty()) return VmNull.withoutDefault();

      var iterator = self.iterator();
      var result = iterator.next();
      while (iterator.hasNext()) {
        var elem = iterator.next();
        var cmpResult = applyLambdaNode.execute(function, elem, result);
        if (cmpResult instanceof Boolean b) {
          if (b) result = elem;
        } else if (cmpResult instanceof Long l) { // deprecated
          if (l < 0) result = elem;
        } else {
          CompilerDirectives.transferToInterpreter();
          throw exceptionBuilder()
              .typeMismatch(cmpResult, BaseModule.getBooleanClass(), BaseModule.getIntClass())
              .build();
        }
      }

      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class max extends ExternalPropertyNode {
    @Child
    @SuppressWarnings("ConstantConditions")
    private GreaterThanNode greaterThanNode =
        GreaterThanNodeGen.create(VmUtils.unavailableSourceSection(), null, null);

    @Specialization
    protected Object eval(VmSet self) {
      self.checkNonEmpty();

      var iterator = self.iterator();
      var result = iterator.next();

      while (iterator.hasNext()) {
        var elem = iterator.next();
        if (greaterThanNode.executeWith(elem, result)) {
          result = elem;
        }
      }

      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class maxOrNull extends ExternalPropertyNode {
    @Child
    @SuppressWarnings("ConstantConditions")
    private GreaterThanNode greaterThanNode =
        GreaterThanNodeGen.create(VmUtils.unavailableSourceSection(), null, null);

    @Specialization
    protected Object eval(VmSet self) {
      if (self.isEmpty()) return VmNull.withoutDefault();

      var iterator = self.iterator();
      var result = iterator.next();

      while (iterator.hasNext()) {
        var elem = iterator.next();
        if (greaterThanNode.executeWith(elem, result)) {
          result = elem;
        }
      }

      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class maxBy extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1NodeGen.create();

    @Child
    @SuppressWarnings("ConstantConditions")
    private GreaterThanNode greaterThanNode =
        GreaterThanNodeGen.create(VmUtils.unavailableSourceSection(), null, null);

    @Specialization
    protected Object eval(VmSet self, VmFunction function) {
      self.checkNonEmpty();

      var iterator = self.iterator();
      var result = iterator.next();
      var resultValue = applyLambdaNode.execute(function, result);

      while (iterator.hasNext()) {
        var elem = iterator.next();
        var elemValue = applyLambdaNode.execute(function, elem);
        if (greaterThanNode.executeWith(elemValue, resultValue)) {
          result = elem;
          resultValue = elemValue;
        }
      }

      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class maxByOrNull extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1NodeGen.create();

    @Child
    @SuppressWarnings("ConstantConditions")
    private GreaterThanNode greaterThanNode =
        GreaterThanNodeGen.create(VmUtils.unavailableSourceSection(), null, null);

    @Specialization
    protected Object eval(VmSet self, VmFunction function) {
      if (self.isEmpty()) return VmNull.withoutDefault();

      var iterator = self.iterator();
      var result = iterator.next();
      var resultValue = applyLambdaNode.execute(function, result);

      while (iterator.hasNext()) {
        var elem = iterator.next();
        var elemValue = applyLambdaNode.execute(function, elem);
        if (greaterThanNode.executeWith(elemValue, resultValue)) {
          result = elem;
          resultValue = elemValue;
        }
      }

      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class maxWith extends ExternalMethod1Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected Object eval(VmSet self, VmFunction function) {
      self.checkNonEmpty();

      var iterator = self.iterator();
      var result = iterator.next();
      while (iterator.hasNext()) {
        var elem = iterator.next();
        var cmpResult = applyLambdaNode.execute(function, elem, result);
        if (cmpResult instanceof Boolean b) {
          if (b) result = elem;
        } else if (cmpResult instanceof Long l) { // deprecated
          if (l < 0) result = elem;
        } else {
          CompilerDirectives.transferToInterpreter();
          throw exceptionBuilder()
              .typeMismatch(cmpResult, BaseModule.getBooleanClass(), BaseModule.getIntClass())
              .build();
        }
      }

      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class maxWithOrNull extends ExternalMethod1Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected Object eval(VmSet self, VmFunction function) {
      if (self.isEmpty()) return VmNull.withoutDefault();

      var iterator = self.iterator();
      var result = iterator.next();
      while (iterator.hasNext()) {
        var elem = iterator.next();
        var cmpResult = applyLambdaNode.execute(function, result, elem);
        if (cmpResult instanceof Boolean b) {
          if (b) result = elem;
        } else if (cmpResult instanceof Long l) { // deprecated
          if (l < 0) result = elem;
        } else {
          CompilerDirectives.transferToInterpreter();
          throw exceptionBuilder()
              .typeMismatch(cmpResult, BaseModule.getBooleanClass(), BaseModule.getIntClass())
              .build();
        }
      }

      LoopNode.reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class sort extends ExternalMethod0Node {
    @Child private CompareNode compareNode = new CompareNode();

    @Specialization
    protected VmList eval(VmSet self) {
      return VmList.create(MergeSort.sort(self.toArray(), compareNode, null));
    }
  }

  public abstract static class sortBy extends ExternalMethod1Node {
    @Child private CompareByNode compareByNode = new CompareByNode();

    @Specialization
    protected VmList eval(VmSet self, VmFunction selector) {
      return VmList.create(MergeSort.sort(self.toArray(), compareByNode, selector));
    }
  }

  public abstract static class sortWith extends ExternalMethod1Node {
    @Child private CompareWithNode compareWithNode = new CompareWithNode();

    @Specialization
    protected VmList eval(VmSet self, VmFunction function) {
      return VmList.create(MergeSort.sort(self.toArray(), compareWithNode, function));
    }
  }

  public abstract static class add extends ExternalMethod1Node {
    @Specialization
    protected VmSet eval(VmSet self, Object element) {
      return self.add(element);
    }
  }

  public abstract static class repeat extends ExternalMethod1Node {
    @Specialization
    protected VmList eval(VmSet self, long n) {
      return self.repeat(n);
    }
  }

  public abstract static class count extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    @Specialization
    protected long eval(VmSet self, VmFunction function) {
      var count = 0;
      for (var elem : self) {
        if (applyLambdaNode.executeBoolean(function, elem)) count += 1;
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return count;
    }
  }

  public abstract static class reverse extends ExternalMethod0Node {
    @Specialization
    protected VmList eval(VmSet self) {
      return self.reverse();
    }
  }

  public abstract static class join extends ExternalMethod1Node {
    @Specialization
    protected String eval(VmSet self, String separator) {
      return self.join(separator);
    }
  }

  public abstract static class zip extends ExternalMethod1Node {
    @Specialization
    protected VmCollection eval(VmSet self, VmCollection coll) {
      return self.zip(coll);
    }
  }

  public abstract static class toList extends ExternalMethod0Node {
    @Specialization
    protected VmList eval(VmSet self) {
      return self.toList();
    }
  }

  public abstract static class toSet extends ExternalMethod0Node {
    @Specialization
    protected VmSet eval(VmSet self) {
      return self;
    }
  }

  public abstract static class toMap extends ExternalMethod2Node {
    @Child private ApplyVmFunction1Node applyKeyExtractorNode = ApplyVmFunction1Node.create();
    @Child private ApplyVmFunction1Node applyValueExtractorNode = ApplyVmFunction1Node.create();

    @Specialization
    protected VmMap eval(VmSet self, VmFunction keyExtractor, VmFunction valueExtractor) {
      var builder = VmMap.builder();

      for (var elem : self) {
        var key = applyKeyExtractorNode.execute(keyExtractor, elem);
        var value = applyValueExtractorNode.execute(valueExtractor, elem);
        builder.add(key, value);
      }

      LoopNode.reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class toListing extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected VmListing eval(VmSet self) {
      return new VmListing(
          VmUtils.createEmptyMaterializedFrame(),
          BaseModule.getListingClass().getPrototype(),
          toObjectMembers(self),
          self.getLength());
    }
  }

  public abstract static class toDynamic extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected VmDynamic eval(VmSet self) {
      return new VmDynamic(
          VmUtils.createEmptyMaterializedFrame(),
          BaseModule.getDynamicClass().getPrototype(),
          toObjectMembers(self),
          self.getLength());
    }
  }

  public abstract static class intersect extends ExternalMethod1Node {
    @Specialization
    protected VmSet eval(VmSet self, VmSet other) {
      VmSet smaller;
      VmSet larger;

      if (self.getLength() <= other.getLength()) {
        smaller = self;
        larger = other;
      } else {
        smaller = other;
        larger = self;
      }

      var builder = VmSet.EMPTY.builder();
      for (var elem : smaller) {
        if (larger.contains(elem)) {
          builder.add(elem);
        }
      }

      return builder.build();
    }
  }

  public abstract static class difference extends ExternalMethod1Node {
    @Specialization
    protected VmSet eval(VmSet self, VmSet other) {
      var builder = VmSet.EMPTY.builder();
      for (var elem : self) {
        if (!other.contains(elem)) {
          builder.add(elem);
        }
      }

      return builder.build();
    }
  }

  private static EconomicMap<Object, ObjectMember> toObjectMembers(VmSet self) {
    var result = EconomicMaps.<Object, ObjectMember>create(self.getLength());

    long idx = 0;
    for (var element : self) {
      EconomicMaps.put(
          result, idx, VmUtils.createSyntheticObjectElement(String.valueOf(idx), element));
      idx += 1;
    }

    return result;
  }
}
