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
package org.pkl.core.stdlib.reflect;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.ast.PklNode;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.util.Pair;

@SuppressWarnings("unused")
public final class DeclaredTypeNodes {
  private static final Identifier REFERENT = Identifier.get("referent");

  private DeclaredTypeNodes() {}

  public abstract static class withTypeArgument extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self, VmTyped typeArgument) {
      var referent = (VmTyped) VmUtils.readMember(self, REFERENT);
      checkTypeArgumentCount(referent, 1, this);
      return MirrorFactories.declaredTypeFactory.create(Pair.of(referent, VmList.of(typeArgument)));
    }
  }

  public abstract static class withTypeArguments extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self, VmList typeArguments) {
      var referent = (VmTyped) VmUtils.readMember(self, REFERENT);
      checkTypeArgumentCount(referent, typeArguments.getLength(), this);
      return MirrorFactories.declaredTypeFactory.create(Pair.of(referent, typeArguments));
    }
  }

  private static void checkTypeArgumentCount(VmTyped referent, int actualCount, PklNode node) {
    var extraStorage = referent.getExtraStorage();
    var typeParameterCount =
        extraStorage instanceof VmClass
            ? ((VmClass) extraStorage).getTypeParameterCount()
            : ((VmTypeAlias) extraStorage).getTypeParameterCount();
    if (typeParameterCount != actualCount) {
      throw new VmExceptionBuilder()
          .evalError("wrongTypeArgumentCount", typeParameterCount, actualCount)
          .withLocation(node)
          .build();
    }
  }
}
