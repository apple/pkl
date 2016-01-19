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
package org.pkl.core.stdlib.base;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import org.pkl.core.ast.lambda.*;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.stdlib.PklName;
import org.pkl.core.util.EconomicMaps;

public final class IntSeqNodes {
  private IntSeqNodes() {}

  public abstract static class start extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmIntSeq self) {
      return self.start;
    }
  }

  public abstract static class end extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmIntSeq self) {
      return self.end;
    }
  }

  public abstract static class step extends ExternalPropertyNode {
    @Specialization
    protected long eval(VmIntSeq self) {
      return self.step;
    }
  }

  @PklName("step")
  public abstract static class stepMethod extends ExternalMethod1Node {
    @Specialization
    protected VmIntSeq eval(VmIntSeq self, long step) {
      return new VmIntSeq(self.start, self.end, step);
    }
  }

  public abstract static class fold extends ExternalMethod2Node {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected Object eval(VmIntSeq self, Object initial, VmFunction function) {
      var result = initial;
      var iter = self.iterator();
      while (iter.hasNext()) {
        result = applyLambdaNode.execute(function, result, iter.nextLong());
      }
      reportLoopCount(this, self.getLength());
      return result;
    }
  }

  public abstract static class map extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    @Specialization
    protected VmList eval(VmIntSeq self, VmFunction function) {
      var builder = VmList.EMPTY.builder();
      var iterator = self.iterator();
      while (iterator.hasNext()) {
        builder.add(applyLambdaNode.execute(function, iterator.nextLong()));
      }
      reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class toList extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected VmList eval(VmIntSeq self) {
      var builder = VmList.EMPTY.builder();
      var iterator = self.iterator();
      while (iterator.hasNext()) {
        builder.add(iterator.nextLong());
      }
      reportLoopCount(this, self.getLength());
      return builder.build();
    }
  }

  public abstract static class toListing extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected VmListing eval(VmIntSeq self) {
      var result = EconomicMaps.<Object, ObjectMember>create();
      var iterator = self.iterator();
      long idx = 0;

      while (iterator.hasNext()) {
        EconomicMaps.put(
            result,
            idx,
            VmUtils.createSyntheticObjectElement(String.valueOf(idx), iterator.nextLong()));
        idx += 1;
      }

      reportLoopCount(this, self.getLength());

      return new VmListing(
          VmUtils.createEmptyMaterializedFrame(),
          BaseModule.getListingClass().getPrototype(),
          result,
          result.size());
    }
  }

  private static void reportLoopCount(Node node, long count) {
    LoopNode.reportLoopCount(node, count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count);
  }
}
