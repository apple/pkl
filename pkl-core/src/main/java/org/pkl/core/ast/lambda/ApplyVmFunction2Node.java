/*
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
package org.pkl.core.ast.lambda;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.pkl.core.ast.PklNode;
import org.pkl.core.runtime.*;

public abstract class ApplyVmFunction2Node extends PklNode {
  public abstract Object execute(VmFunction function, Object arg1, Object arg2);

  public final boolean executeBoolean(VmFunction function, Object arg1, Object arg2) {
    var result = execute(function, arg1, arg2);
    if (result instanceof Boolean b) return b;

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder().typeMismatch(result, BaseModule.getBooleanClass()).build();
  }

  public final VmCollection executeCollection(VmFunction function, Object arg1, Object arg2) {
    var result = execute(function, arg1, arg2);
    if (result instanceof VmCollection collection) return collection;

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder().typeMismatch(result, BaseModule.getCollectionClass()).build();
  }

  public final VmMap executeMap(VmFunction function, Object arg1, Object arg2) {
    var result = execute(function, arg1, arg2);
    if (result instanceof VmMap map) return map;

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder().typeMismatch(result, BaseModule.getMapClass()).build();
  }

  public final Long executeInt(VmFunction function, Object arg1, Object arg2) {
    var result = execute(function, arg1, arg2);
    if (result instanceof Long l) return l;

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder().typeMismatch(result, BaseModule.getIntClass()).build();
  }

  public final VmPair executePair(VmFunction function, Object arg1, Object arg2) {
    var result = execute(function, arg1, arg2);
    if (result instanceof VmPair pair) return pair;

    CompilerDirectives.transferToInterpreter();
    throw exceptionBuilder().typeMismatch(result, BaseModule.getPairClass()).build();
  }

  @Specialization(guards = "function.getCallTarget() == cachedCallTarget")
  protected Object evalDirect(
      VmFunction function,
      Object arg1,
      Object arg2,
      @SuppressWarnings("unused") @Cached("function.getCallTarget()")
          RootCallTarget cachedCallTarget,
      @Cached("create(cachedCallTarget)") DirectCallNode callNode) {

    return callNode.call(function.getThisValue(), function, arg1, arg2);
  }

  @Specialization(replaces = "evalDirect")
  protected Object eval(
      VmFunction function,
      Object arg1,
      Object arg2,
      @Cached("create()") IndirectCallNode callNode) {

    return callNode.call(function.getCallTarget(), function.getThisValue(), function, arg1, arg2);
  }
}
