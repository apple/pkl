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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.LoopNode;
import java.util.Map;
import org.pkl.core.ast.lambda.*;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.MutableReference;

public final class MapNodes {
  private MapNodes() {}

  public abstract static class getOrNull extends ExternalMethod1Node {
    @Specialization
    protected Object eval(VmMap self, Object key) {
      return self.getOrVmNull(key);
    }
  }

  public abstract static class length extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmMap self) {
      return self.getLength();
    }
  }

  public abstract static class isEmpty extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(VmMap self) {
      return self.isEmpty();
    }
  }

  public abstract static class keys extends ExternalPropertyNode {
    @Specialization
    protected VmSet eval(VmMap self) {
      return self.keys();
    }
  }

  public abstract static class values extends ExternalPropertyNode {
    @Specialization
    protected VmList eval(VmMap self) {
      return self.values();
    }
  }

  public abstract static class entries extends ExternalPropertyNode {
    @Specialization
    protected VmList eval(VmMap self) {
      return self.entries();
    }
  }

  public abstract static class containsKey extends ExternalMethod1Node {
    @Specialization
    protected boolean eval(VmMap self, Object key) {
      return self.containsKey(key);
    }
  }

  public abstract static class containsValue extends ExternalMethod1Node {
    @Specialization
    protected boolean eval(VmMap self, Object value) {
      return self.containsValue(value);
    }
  }

  public abstract static class put extends ExternalMethod2Node {
    @Specialization
    protected VmMap eval(VmMap self, Object key, Object value) {
      return self.put(key, value);
    }
  }

  public abstract static class remove extends ExternalMethod1Node {
    @Specialization
    protected VmMap eval(VmMap self, Object key) {
      return self.remove(key);
    }
  }

  public abstract static class filter extends ExternalMethod1Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected VmMap eval(VmMap self, VmFunction function) {
      var builder = VmMap.builder();
      for (var entry : self) {
        var key = VmUtils.getKey(entry);
        var value = VmUtils.getValue(entry);
        if (applyLambdaNode.executeBoolean(function, key, value)) {
          builder.add(key, value);
        }
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class fold extends ExternalMethod2Node {
    @Child private ApplyVmFunction3Node applyLambdaNode = ApplyVmFunction3NodeGen.create();

    @Specialization
    protected Object eval(VmMap self, Object initial, VmFunction function) {
      var result = new MutableReference<>(initial);
      for (var entry : self) {
        result.set(
            applyLambdaNode.execute(
                function, result.get(), VmUtils.getKey(entry), VmUtils.getValue(entry)));
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return result.get();
    }
  }

  public abstract static class map extends ExternalMethod1Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected VmMap eval(VmMap self, VmFunction function) {
      var builder = VmMap.builder();
      for (var entry : self) {
        var pair =
            applyLambdaNode.executePair(function, VmUtils.getKey(entry), VmUtils.getValue(entry));
        builder.add(pair.getFirst(), pair.getSecond());
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class mapKeys extends ExternalMethod1Node {
    @Specialization
    protected VmMap eval(
        VmMap self, VmFunction function, @Cached("create()") ApplyVmFunction2Node applyNode) {
      var builder = VmMap.builder();
      for (var entry : self) {
        builder.add(
            applyNode.execute(function, VmUtils.getKey(entry), VmUtils.getValue(entry)),
            VmUtils.getValue(entry));
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class mapValues extends ExternalMethod1Node {
    @Specialization
    protected VmMap eval(
        VmMap self, VmFunction function, @Cached("create()") ApplyVmFunction2Node applyNode) {
      var builder = VmMap.builder();
      for (var entry : self) {
        builder.add(
            VmUtils.getKey(entry),
            applyNode.execute(function, VmUtils.getKey(entry), VmUtils.getValue(entry)));
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class flatMap extends ExternalMethod1Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected VmMap eval(VmMap self, VmFunction function) {
      var builder = VmMap.builder();
      for (var entry : self) {
        VmMap flatMapResult =
            applyLambdaNode.executeMap(function, VmUtils.getKey(entry), VmUtils.getValue(entry));

        for (Map.Entry<Object, Object> flatMapEntry : flatMapResult) {
          builder.add(VmUtils.getKey(flatMapEntry), VmUtils.getValue(flatMapEntry));
        }
      }
      LoopNode.reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class every extends ExternalMethod1Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected boolean eval(VmMap self, VmFunction function) {
      for (var entry : self) {
        if (!applyLambdaNode.executeBoolean(
            function, VmUtils.getKey(entry), VmUtils.getValue(entry))) return false;
      }
      return true;
    }
  }

  public abstract static class any extends ExternalMethod1Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected boolean eval(VmMap self, VmFunction function) {
      for (var entry : self) {
        if (applyLambdaNode.executeBoolean(
            function, VmUtils.getKey(entry), VmUtils.getValue(entry))) return true;
      }
      return false;
    }
  }

  public abstract static class toMap extends ExternalMethod0Node {
    @Specialization
    protected VmMap eval(VmMap self) {
      return self;
    }
  }

  public abstract static class toDynamic extends ExternalMethod0Node {
    @Specialization
    protected VmDynamic eval(VmMap self) {
      var members = EconomicMaps.<Object, ObjectMember>create(self.getLength());

      for (var entry : self) {
        var key = VmUtils.getKey(entry);

        if (key instanceof String string) {
          var name = Identifier.get(string);
          EconomicMaps.put(
              members,
              name,
              VmUtils.createSyntheticObjectProperty(name, "", VmUtils.getValue(entry)));
        } else {
          EconomicMaps.put(
              members, key, VmUtils.createSyntheticObjectEntry("", VmUtils.getValue(entry)));
        }
      }

      return new VmDynamic(
          VmUtils.createEmptyMaterializedFrame(),
          BaseModule.getDynamicClass().getPrototype(),
          members,
          0);
    }
  }

  public abstract static class toTyped extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmMap self, VmClass clazz) {
      if (!clazz.isSubclassOf(BaseModule.getTypedClass())) {
        throw exceptionBuilder().evalError("notASubclassOfTyped", clazz).build();
      }

      VmUtils.checkIsInstantiable(clazz, null);

      var result =
          new VmTyped(
              VmUtils.createEmptyMaterializedFrame(),
              clazz.getPrototype(),
              clazz,
              clazz.getMapToTypedMembers());
      result.setExtraStorage(self);
      return result;
    }
  }

  public abstract static class toMapping extends ExternalMethod0Node {
    @Specialization
    protected VmMapping eval(VmMap self) {
      var members = EconomicMaps.<Object, ObjectMember>create(self.getLength());

      for (var entry : self) {
        EconomicMaps.put(
            members,
            VmUtils.getKey(entry),
            VmUtils.createSyntheticObjectEntry("", VmUtils.getValue(entry)));
      }

      return new VmMapping(
          VmUtils.createEmptyMaterializedFrame(),
          BaseModule.getMappingClass().getPrototype(),
          members);
    }
  }
}
