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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.pkl.core.runtime.VmIntSeq;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmPair;
import org.pkl.core.runtime.VmReference;
import org.pkl.core.runtime.VmRegex;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.runtime.VmValue;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.ExternalPropertyNode;

/**
 * External property and function implementations for the `base` module.
 *
 * <p>To reduce the chance of base module properties shadowing object properties, the only use of
 * properties in the base module is for logical constants (e.g. MaxInt), and they all start with an
 * uppercase letter.
 *
 * <p>For the same reason, the only use of methods in the base module is for constructor methods
 * (e.g. Pair()), and they all start with an uppercase letter.
 *
 * <p>Note that having base module *properties* read language context specific information (e.g.,
 * CLI args) would lead to incorrect behavior, as a single base module instance is shared across all
 * language contexts, and properties (whether external or not) are evaluated just once.
 */
@SuppressWarnings("UnusedParameters")
public final class BaseNodes {
  private BaseNodes() {}

  public abstract static class NaN extends ExternalPropertyNode {
    @Specialization
    protected double eval(VmTyped ignored) {
      return Double.NaN;
    }
  }

  public abstract static class Infinity extends ExternalPropertyNode {
    @Specialization
    protected double eval(VmTyped ignored) {
      return Double.POSITIVE_INFINITY;
    }
  }

  public abstract static class Regex extends ExternalMethod1Node {
    // cache Regex object to avoid repeated java.util.Pattern.compile()
    @Specialization(guards = "pattern.equals(cachedPattern)")
    protected VmRegex evalCached(
        VirtualFrame frame,
        VmTyped self,
        String pattern,
        @Cached("pattern") String cachedPattern,
        @Cached("createRegex(frame, pattern)") VmRegex cachedRegex) {

      return cachedRegex;
    }

    @Specialization(replaces = "evalCached")
    protected VmRegex eval(VirtualFrame frame, VmTyped self, String pattern) {
      return createRegex(frame, pattern);
    }

    protected VmRegex createRegex(VirtualFrame frame, String pattern) {
      return new VmRegex(VmUtils.compilePattern(pattern, getArg1Node()));
    }
  }

  public abstract static class Undefined extends ExternalMethod0Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self) {
      throw exceptionBuilder().undefinedValue().build();
    }
  }

  public abstract static class Null extends ExternalMethod1Node {
    @Specialization
    protected VmNull eval(VmTyped self, Object defaultValue) {
      return VmNull.withDefault(defaultValue);
    }
  }

  public abstract static class List extends ExternalMethod1Node {
    @Specialization
    protected VmList eval(VirtualFrame frame, VmTyped self, Object args) {
      // invocations of this method are handled specially in AstBuilder
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder().bug("Node `BaseNodes.List` should never be executed.").build();
    }
  }

  public abstract static class Set extends ExternalMethod1Node {
    @Specialization
    protected VmList eval(VirtualFrame frame, VmTyped self, Object args) {
      // invocations of this method are handled specially in AstBuilder
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder().bug("Node `BaseNodes.Set` should never be executed.").build();
    }
  }

  public abstract static class Map extends ExternalMethod1Node {
    @Specialization
    protected VmList eval(VirtualFrame frame, VmTyped self, Object args) {
      // invocations of this method are handled specially in AstBuilder
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder().bug("Node `BaseNodes.Map` should never be executed.").build();
    }
  }

  public abstract static class Pair extends ExternalMethod2Node {
    @Specialization
    protected VmPair eval(VirtualFrame frame, VmTyped self, Object first, Object second) {
      return new VmPair(first, second);
    }
  }

  public abstract static class IntSeq extends ExternalMethod2Node {
    @Specialization
    protected VmIntSeq eval(VirtualFrame frame, VmTyped self, long first, long second) {
      return new VmIntSeq(first, second, 1L);
    }
  }

  public abstract static class Bytes extends ExternalMethod1Node {
    @Specialization
    protected VmList eval(VirtualFrame frame, VmTyped self, Object args) {
      // invocations of this method are handled specially in AstBuilder
      CompilerDirectives.transferToInterpreter();
      throw exceptionBuilder().bug("Node `BaseNodes.Bytes` should never be executed.").build();
    }
  }

  public abstract static class Reference extends ExternalMethod1Node {
    @Specialization
    protected VmReference eval(VirtualFrame frame, VmTyped self, VmValue rootValue) {
      return new VmReference(rootValue);
    }
  }
}
