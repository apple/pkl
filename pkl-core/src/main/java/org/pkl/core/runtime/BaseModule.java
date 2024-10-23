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
package org.pkl.core.runtime;

import static org.pkl.core.PClassInfo.pklBaseUri;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class BaseModule extends StdLibModule {
  static final VmTyped instance = VmUtils.createEmptyModule();

  static {
    loadModule(pklBaseUri, instance);
  }

  public static VmTyped getModule() {
    return instance;
  }

  public static VmClass getAnyClass() {
    return AnyClass.instance;
  }

  public static VmClass getTypedClass() {
    return TypedClass.instance;
  }

  public static VmClass getNullClass() {
    return NullClass.instance;
  }

  public static VmClass getNumberClass() {
    return NumberClass.instance;
  }

  public static VmClass getIntClass() {
    return IntClass.instance;
  }

  public static VmClass getFloatClass() {
    return FloatClass.instance;
  }

  public static VmClass getStringClass() {
    return StringClass.instance;
  }

  public static VmClass getBooleanClass() {
    return BooleanClass.instance;
  }

  public static VmClass getDurationClass() {
    return DurationClass.instance;
  }

  public static VmClass getDataSizeClass() {
    return DataSizeClass.instance;
  }

  public static VmClass getIntSeqClass() {
    return IntSeqClass.instance;
  }

  public static VmClass getCollectionClass() {
    return CollectionClass.instance;
  }

  public static VmClass getListClass() {
    return ListClass.instance;
  }

  public static VmClass getSetClass() {
    return SetClass.instance;
  }

  public static VmClass getListingClass() {
    return ListingClass.instance;
  }

  public static VmClass getMapClass() {
    return MapClass.instance;
  }

  public static VmClass getMappingClass() {
    return MappingClass.instance;
  }

  public static VmClass getDynamicClass() {
    return DynamicClass.instance;
  }

  public static VmClass getRenderDirectiveClass() {
    return RenderDirectiveClass.instance;
  }

  /**
   * Returns class pkl.base#Module. For the module class of pkl.base use {@code
   * getModule().getVmClass()}.
   */
  public static VmClass getModuleClass() {
    return ModuleClass.instance;
  }

  public static VmClass getClassClass() {
    return ClassClass.instance;
  }

  public static VmClass getTypeAliasClass() {
    return TypeAliasClass.instance;
  }

  public static VmClass getRegexClass() {
    return RegexClass.instance;
  }

  public static VmClass getRegexMatchClass() {
    return RegexMatchClass.instance;
  }

  public static VmClass getFunctionClass() {
    return FunctionClass.instance;
  }

  public static VmClass getFunctionNClass(int paramCount) {
    return switch (paramCount) {
      case 0 -> getFunction0Class();
      case 1 -> getFunction1Class();
      case 2 -> getFunction2Class();
      case 3 -> getFunction3Class();
      case 4 -> getFunction4Class();
      case 5 -> getFunction5Class();
      default -> {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalArgumentException(
            String.format("Class `Function%d` does not exist.", paramCount));
      }
    };
  }

  public static VmClass getFunction0Class() {
    return Function0Class.instance;
  }

  public static VmClass getFunction1Class() {
    return Function1Class.instance;
  }

  public static VmClass getFunction2Class() {
    return Function2Class.instance;
  }

  public static VmClass getFunction3Class() {
    return Function3Class.instance;
  }

  public static VmClass getFunction4Class() {
    return Function4Class.instance;
  }

  public static VmClass getFunction5Class() {
    return Function5Class.instance;
  }

  public static VmClass getPairClass() {
    return PairClass.instance;
  }

  public static VmClass getVarArgsClass() {
    return VarArgsClass.instance;
  }

  public static VmClass getModuleInfoClass() {
    return ModuleInfoClass.instance;
  }

  public static VmClass getAnnotationClass() {
    return AnnotationClass.instance;
  }

  public static VmClass getDeprecatedClass() {
    return DeprecatedClass.instance;
  }

  public static VmClass getResourceClass() {
    return ResourceClass.instance;
  }

  public static VmTypeAlias getNonNullTypeAlias() {
    return NonNullTypeAlias.instance;
  }

  public static VmTypeAlias getInt8TypeAlias() {
    return Int8TypeAlias.instance;
  }

  public static VmTypeAlias getInt16TypeAlias() {
    return Int16TypeAlias.instance;
  }

  public static VmTypeAlias getInt32TypeAlias() {
    return Int32TypeAlias.instance;
  }

  public static VmTypeAlias getMixinTypeAlias() {
    return MixinTypeAlias.instance;
  }

  private static final class AnyClass {
    static final VmClass instance = loadClass("Any");
  }

  private static final class TypedClass {
    static final VmClass instance = loadClass("Typed");
  }

  private static final class NullClass {
    static final VmClass instance = loadClass("Null");
  }

  private static final class NumberClass {
    static final VmClass instance = loadClass("Number");
  }

  private static final class IntClass {
    static final VmClass instance = loadClass("Int");
  }

  private static final class FloatClass {
    static final VmClass instance = loadClass("Float");
  }

  private static final class StringClass {
    static final VmClass instance = loadClass("String");
  }

  private static final class BooleanClass {
    static final VmClass instance = loadClass("Boolean");
  }

  private static final class DurationClass {
    static final VmClass instance = loadClass("Duration");
  }

  private static final class DataSizeClass {
    static final VmClass instance = loadClass("DataSize");
  }

  private static final class IntSeqClass {
    static final VmClass instance = loadClass("IntSeq");
  }

  private static final class CollectionClass {
    static final VmClass instance = loadClass("Collection");
  }

  private static final class ListClass {
    static final VmClass instance = loadClass("List");
  }

  private static final class SetClass {
    static final VmClass instance = loadClass("Set");
  }

  private static final class ListingClass {
    static final VmClass instance = loadClass("Listing");
  }

  private static final class MapClass {
    static final VmClass instance = loadClass("Map");
  }

  private static final class MappingClass {
    static final VmClass instance = loadClass("Mapping");
  }

  private static final class DynamicClass {
    static final VmClass instance = loadClass("Dynamic");
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

  private static final class RegexClass {
    static final VmClass instance = loadClass("Regex");
  }

  private static final class RegexMatchClass {
    static final VmClass instance = loadClass("RegexMatch");
  }

  private static final class RenderDirectiveClass {
    static final VmClass instance = loadClass("RenderDirective");
  }

  private static final class PairClass {
    static final VmClass instance = loadClass("Pair");
  }

  private static final class VarArgsClass {
    static final VmClass instance = loadClass("VarArgs");
  }

  private static final class ModuleInfoClass {
    static final VmClass instance = loadClass("ModuleInfo");
  }

  private static final class AnnotationClass {
    static final VmClass instance = loadClass("Annotation");
  }

  private static final class DeprecatedClass {
    static final VmClass instance = loadClass("Deprecated");
  }

  private static final class ResourceClass {
    static final VmClass instance = loadClass("Resource");
  }

  private static final class FunctionClass {
    static final VmClass instance = loadClass("Function");
  }

  private static final class Function0Class {
    static final VmClass instance = loadClass("Function0");
  }

  private static final class Function1Class {
    static final VmClass instance = loadClass("Function1");
  }

  private static final class Function2Class {
    static final VmClass instance = loadClass("Function2");
  }

  private static final class Function3Class {
    static final VmClass instance = loadClass("Function3");
  }

  private static final class Function4Class {
    static final VmClass instance = loadClass("Function4");
  }

  private static final class Function5Class {
    static final VmClass instance = loadClass("Function5");
  }

  private static final class NonNullTypeAlias {
    static final VmTypeAlias instance = loadTypeAlias("NonNull");
  }

  private static final class Int8TypeAlias {
    static final VmTypeAlias instance = loadTypeAlias("Int8");
  }

  private static final class Int16TypeAlias {
    static final VmTypeAlias instance = loadTypeAlias("Int16");
  }

  private static final class Int32TypeAlias {
    static final VmTypeAlias instance = loadTypeAlias("Int32");
  }

  private static final class MixinTypeAlias {
    static final VmTypeAlias instance = loadTypeAlias("Mixin");
  }

  @TruffleBoundary
  private static VmClass loadClass(String className) {
    var theModule = getModule();
    return (VmClass) VmUtils.readMember(theModule, Identifier.get(className));
  }

  @TruffleBoundary
  private static VmTypeAlias loadTypeAlias(String typeAliasName) {
    var theModule = getModule();
    return (VmTypeAlias) VmUtils.readMember(theModule, Identifier.get(typeAliasName));
  }
}
