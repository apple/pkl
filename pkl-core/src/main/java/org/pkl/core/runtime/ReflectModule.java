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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.net.URI;

public final class ReflectModule extends StdLibModule {
  private static final VmTyped instance = VmUtils.createEmptyModule();

  static {
    // Forcing of `instance` inside `loadModule()` results in calls to methods such as
    // `ReflectModule.getNullableTypeClass()` via `MirrorFactories`.
    // By calling loadModule() in the outer class's static initializer (rather than having another
    // nested class for this),
    // initialization loops (e.g., during execution of native-image) are avoided.
    loadModule(URI.create("pkl:reflect"), instance);
  }

  private ReflectModule() {}

  public static VmTyped getModule() {
    return instance;
  }

  public static VmClass getModuleClass() {
    return ModuleClass.instance;
  }

  public static VmClass getClassClass() {
    return ClassClass.instance;
  }

  public static VmClass getTypeAliasClass() {
    return TypeAliasClass.instance;
  }

  public static VmClass getPropertyClass() {
    return PropertyClass.instance;
  }

  public static VmClass getMethodClass() {
    return MethodClass.instance;
  }

  public static VmClass getMethodParameterClass() {
    return MethodParameterClass.instance;
  }

  public static VmClass getTypeParameterClass() {
    return TypeParameterClass.instance;
  }

  public static VmClass getDeclaredTypeClass() {
    return DeclaredTypeClass.instance;
  }

  public static VmClass getStringLiteralTypeClass() {
    return StringLiteralTypeClass.instance;
  }

  public static VmClass getUnionTypeClass() {
    return UnionTypeClass.instance;
  }

  public static VmClass getNullableTypeClass() {
    return NullableTypeClass.instance;
  }

  public static VmClass getModuleTypeClass() {
    return ModuleTypeClass.instance;
  }

  public static VmClass getFunctionTypeClass() {
    return FunctionTypeClass.instance;
  }

  public static VmClass getUnknownTypeClass() {
    return UnknownTypeClass.instance;
  }

  public static VmClass getNothingTypeClass() {
    return NothingTypeClass.instance;
  }

  public static VmClass getTypeVariableClass() {
    return TypeVariableClass.instance;
  }

  public static VmClass getSourceLocationClass() {
    return SourceLocationClass.instance;
  }

  private static final class ModuleClass {
    static final VmClass instance = loadClass("Module");
  }

  private static final class ClassClass {
    static final VmClass instance = loadClass("Class");
  }

  private static final class TypeAliasClass {
    static final VmClass instance = loadClass("TypeAlias");
  }

  private static final class PropertyClass {
    static final VmClass instance = loadClass("Property");
  }

  private static final class MethodClass {
    static final VmClass instance = loadClass("Method");
  }

  private static final class MethodParameterClass {
    static final VmClass instance = loadClass("MethodParameter");
  }

  private static final class TypeParameterClass {
    static final VmClass instance = loadClass("TypeParameter");
  }

  private static final class DeclaredTypeClass {
    static final VmClass instance = loadClass("DeclaredType");
  }

  private static final class StringLiteralTypeClass {
    static final VmClass instance = loadClass("StringLiteralType");
  }

  private static final class UnionTypeClass {
    static final VmClass instance = loadClass("UnionType");
  }

  private static final class NullableTypeClass {
    static final VmClass instance = loadClass("NullableType");
  }

  private static final class ModuleTypeClass {
    static final VmClass instance = loadClass("ModuleType");
  }

  private static final class FunctionTypeClass {
    static final VmClass instance = loadClass("FunctionType");
  }

  private static final class UnknownTypeClass {
    static final VmClass instance = loadClass("UnknownType");
  }

  private static final class NothingTypeClass {
    static final VmClass instance = loadClass("NothingType");
  }

  private static final class TypeVariableClass {
    static final VmClass instance = loadClass("TypeVariable");
  }

  private static final class SourceLocationClass {
    static final VmClass instance = loadClass("SourceLocation");
  }

  @TruffleBoundary
  private static VmClass loadClass(String className) {
    var theModule = getModule();
    return (VmClass) VmUtils.readMember(theModule, Identifier.get(className));
  }
}
