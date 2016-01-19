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
import org.pkl.core.ast.PklNode;
import org.pkl.core.ast.expression.binary.LessThanNode;
import org.pkl.core.ast.expression.binary.LessThanNodeGen;
import org.pkl.core.ast.lambda.ApplyVmFunction1Node;
import org.pkl.core.ast.lambda.ApplyVmFunction1NodeGen;
import org.pkl.core.ast.lambda.ApplyVmFunction2Node;
import org.pkl.core.ast.lambda.ApplyVmFunction2NodeGen;
import org.pkl.core.runtime.BaseModule;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.Nullable;

public final class CollectionNodes {
  private CollectionNodes() {}

  public abstract static class SortComparatorNode extends PklNode {
    public abstract boolean executeWith(Object left, Object right, @Nullable VmFunction function);
  }

  public static final class CompareNode extends SortComparatorNode {
    @Child
    @SuppressWarnings("ConstantConditions")
    private LessThanNode lessThanNode =
        LessThanNodeGen.create(VmUtils.unavailableSourceSection(), null, null);

    @Override
    public boolean executeWith(Object left, Object right, @Nullable VmFunction function) {
      assert function == null;
      return lessThanNode.executeWith(left, right);
    }
  }

  public static final class CompareByNode extends SortComparatorNode {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1NodeGen.create();

    @Child
    @SuppressWarnings("ConstantConditions")
    private LessThanNode lessThanNode =
        LessThanNodeGen.create(VmUtils.unavailableSourceSection(), null, null);

    @Override
    public boolean executeWith(Object left, Object right, @Nullable VmFunction selector) {
      assert selector != null;
      var leftResult = applyLambdaNode.execute(selector, left);
      var rightResult = applyLambdaNode.execute(selector, right);
      return lessThanNode.executeWith(leftResult, rightResult);
    }
  }

  public static final class CompareWithNode extends SortComparatorNode {
    @Child private ApplyVmFunction2Node applyLambdaNode = ApplyVmFunction2NodeGen.create();

    @Override
    public boolean executeWith(Object left, Object right, @Nullable VmFunction comparator) {
      assert comparator != null;
      var result = applyLambdaNode.execute(comparator, left, right);

      if (result instanceof Boolean) return (Boolean) result;

      // deprecated
      if (result instanceof Long) return (long) result < 0;

      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder()
          .typeMismatch(result, BaseModule.getBooleanClass(), BaseModule.getIntClass())
          .build();
    }
  }
}
