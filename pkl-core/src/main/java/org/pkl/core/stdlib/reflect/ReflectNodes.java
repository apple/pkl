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
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.util.Pair;

@SuppressWarnings("unused")
public final class ReflectNodes {
  private ReflectNodes() {}

  public abstract static class Module extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self, VmTyped module) {
      if (!module.isModuleObject()) {
        throw exceptionBuilder().evalError("expectedModule").withLocation(getArg1Node()).build();
      }
      return module.getModuleMirror();
    }
  }

  public abstract static class moduleOf extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self, VmTyped module) {
      var candidate = module;
      while (!candidate.isModuleObject()) {
        candidate = candidate.getParent();
        if (candidate == null) {
          throw exceptionBuilder()
              .bug("No module found in prototype chain.")
              .withLocation(getArg1Node())
              .build();
        }
      }

      return candidate.getModuleMirror();
    }
  }

  public abstract static class Class extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self, VmClass clazz) {
      return clazz.getMirror();
    }
  }

  public abstract static class TypeAlias extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self, VmTypeAlias typeAlias) {
      return typeAlias.getMirror();
    }
  }

  public abstract static class DeclaredType extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self, VmTyped referent) {
      var extraStorage = referent.getExtraStorage();
      assert extraStorage instanceof VmClass || extraStorage instanceof VmTypeAlias;

      var typeParameterCount =
          extraStorage instanceof VmClass vmClass
              ? vmClass.getTypeParameterCount()
              : ((VmTypeAlias) extraStorage).getTypeParameterCount();

      var builder = VmList.EMPTY.builder();
      for (var i = 0; i < typeParameterCount; i++) {
        builder.add(MirrorFactories.unknownTypeFactory.create(null));
      }

      return MirrorFactories.declaredTypeFactory.create(Pair.of(referent, builder.build()));
    }
  }

  public abstract static class FunctionType extends ExternalMethod2Node {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self, VmList parameterTypes, VmTyped returnType) {
      return MirrorFactories.functionTypeFactory2.create(Pair.of(parameterTypes, returnType));
    }
  }

  public abstract static class StringLiteralType extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self, String value) {
      return MirrorFactories.stringLiteralTypeFactory2.create(value);
    }
  }

  public abstract static class UnionType extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self, VmList members) {
      return MirrorFactories.unionTypeFactory2.create(members);
    }
  }

  public abstract static class TypeVariable extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self, VmTyped referent) {
      return MirrorFactories.typeVariableFactory2.create(referent);
    }
  }

  public abstract static class moduleType extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self) {
      return MirrorFactories.moduleTypeFactory.create(null);
    }
  }

  public abstract static class unknownType extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self) {
      return MirrorFactories.unknownTypeFactory.create(null);
    }
  }

  public abstract static class nothingType extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self) {
      return MirrorFactories.nothingTypeFactory.create(null);
    }
  }
}
