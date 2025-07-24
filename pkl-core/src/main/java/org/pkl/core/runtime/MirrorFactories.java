/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.TypeParameter;
import org.pkl.core.ast.member.ClassMethod;
import org.pkl.core.ast.member.ClassProperty;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.TypeNode.*;
import org.pkl.core.stdlib.VmObjectFactory;
import org.pkl.core.stdlib.VmObjectFactory.Property;
import org.pkl.core.util.Pair;

public final class MirrorFactories {
  private MirrorFactories() {}

  public static final VmObjectFactory<VmTyped> moduleFactory =
      new VmObjectFactory<>(ReflectModule::getModuleClass);

  public static final VmObjectFactory<VmClass> classFactory =
      new VmObjectFactory<>(ReflectModule::getClassClass);

  public static final VmObjectFactory<VmTypeAlias> typeAliasFactory =
      new VmObjectFactory<>(ReflectModule::getTypeAliasClass);

  public static final VmObjectFactory<ClassProperty.Mirror> propertyFactory =
      new VmObjectFactory<>(ReflectModule::getPropertyClass);

  public static final VmObjectFactory<ClassMethod> methodFactory =
      new VmObjectFactory<>(ReflectModule::getMethodClass);

  public static final VmObjectFactory<Pair<String, VmTyped>> methodParameterFactory =
      new VmObjectFactory<>(ReflectModule::getMethodParameterClass);

  public static final VmObjectFactory<TypeParameter> typeParameterFactory =
      new VmObjectFactory<>(ReflectModule::getTypeParameterClass);

  public static final VmObjectFactory<TypeNode> classTypeFactory =
      new VmObjectFactory<>(ReflectModule::getDeclaredTypeClass);

  public static final VmObjectFactory<TypeNode> typeAliasTypeFactory =
      new VmObjectFactory<>(ReflectModule::getDeclaredTypeClass);

  public static final VmObjectFactory<Pair<VmTyped, VmList>> declaredTypeFactory =
      new VmObjectFactory<>(ReflectModule::getDeclaredTypeClass);

  public static final VmObjectFactory<StringLiteralTypeNode> stringLiteralTypeFactory =
      new VmObjectFactory<>(ReflectModule::getStringLiteralTypeClass);

  public static final VmObjectFactory<String> stringLiteralTypeFactory2 =
      new VmObjectFactory<>(ReflectModule::getStringLiteralTypeClass);

  public static final VmObjectFactory<UnionTypeNode> unionTypeFactory =
      new VmObjectFactory<>(ReflectModule::getUnionTypeClass);

  public static final VmObjectFactory<VmList> unionTypeFactory2 =
      new VmObjectFactory<>(ReflectModule::getUnionTypeClass);

  public static final VmObjectFactory<UnionOfStringLiteralsTypeNode>
      unionOfStringLiteralsTypeFactory = new VmObjectFactory<>(ReflectModule::getUnionTypeClass);

  public static final VmObjectFactory<VmList> unionOfStringLiteralsTypeFactory2 =
      new VmObjectFactory<>(ReflectModule::getUnionTypeClass);

  public static final VmObjectFactory<NullableTypeNode> nullableTypeFactory =
      new VmObjectFactory<>(ReflectModule::getNullableTypeClass);

  public static final VmObjectFactory<VmTyped> nullableTypeFactory2 =
      new VmObjectFactory<>(ReflectModule::getNullableTypeClass);

  public static final VmObjectFactory<FunctionTypeNode> functionTypeFactory =
      new VmObjectFactory<>(ReflectModule::getFunctionTypeClass);

  public static final VmObjectFactory<Pair<VmList, VmTyped>> functionTypeFactory2 =
      new VmObjectFactory<>(ReflectModule::getFunctionTypeClass);

  public static final VmObjectFactory<TypeVariableNode> typeVariableFactory =
      new VmObjectFactory<>(ReflectModule::getTypeVariableClass);

  public static final VmObjectFactory<VmTyped> typeVariableFactory2 =
      new VmObjectFactory<>(ReflectModule::getTypeVariableClass);

  public static final VmObjectFactory<Void> moduleTypeFactory =
      new VmObjectFactory<>(ReflectModule::getModuleTypeClass);

  public static final VmObjectFactory<Void> unknownTypeFactory =
      new VmObjectFactory<>(ReflectModule::getUnknownTypeClass);

  public static final VmObjectFactory<Void> nothingTypeFactory =
      new VmObjectFactory<>(ReflectModule::getNothingTypeClass);

  public static final VmObjectFactory<SourceSection> sourceLocationFactory =
      new VmObjectFactory<>(ReflectModule::getSourceLocationClass);

  static {
    moduleFactory
        .addTypedProperty(
            "location",
            module -> sourceLocationFactory.create(module.getModuleInfo().getHeaderSection()))
        .addProperty(
            "docComment",
            module -> VmNull.lift(VmUtils.exportDocComment(module.getModuleInfo().getDocComment())))
        .addListProperty(
            "annotations", module -> VmList.create(module.getModuleInfo().getAnnotations()))
        .addSetProperty(
            "modifiers",
            module ->
                module.getModuleInfo().isAmend()
                    ? VmSet.EMPTY
                    : module.getVmClass().getModifierMirrors())
        .addStringProperty("name", module -> module.getModuleInfo().getModuleName())
        .addStringProperty(
            "uri", module -> module.getModuleInfo().getModuleKey().getUri().toString())
        .addTypedProperty("reflectee", Property.identity())
        .addTypedProperty("moduleClass", module -> module.getVmClass().getMirror())
        .addProperty("supermodule", VmTyped::getSupermoduleMirror)
        .addBooleanProperty("isAmend", module -> module.getModuleInfo().isAmend())
        .addMapProperty("imports", VmTyped::getImports)
        .addMapProperty("classes", VmTyped::getClassMirrors)
        .addMapProperty("typeAliases", VmTyped::getTypeAliasMirrors);

    classFactory
        .addTypedProperty(
            "location", clazz -> sourceLocationFactory.create(clazz.getHeaderSection()))
        .addProperty(
            "docComment", clazz -> VmNull.lift(VmUtils.exportDocComment(clazz.getDocComment())))
        .addListProperty("annotations", clazz -> VmList.create(clazz.getAnnotations()))
        .addSetProperty("modifiers", VmClass::getModifierMirrors)
        .addStringProperty("name", VmClass::getSimpleName)
        .addClassProperty("reflectee", Property.identity())
        .addListProperty("typeParameters", VmClass::getTypeParameterMirrors)
        .addProperty("superclass", VmClass::getSuperclassMirror)
        .addProperty("supertype", VmClass::getSupertypeMirror)
        .addMapProperty("properties", VmClass::getPropertyMirrors)
        .addMapProperty("allProperties", VmClass::getAllPropertyMirrors)
        .addTypedProperty("enclosingDeclaration", VmClass::getModuleMirror)
        .addMapProperty("methods", VmClass::getMethodMirrors)
        .addMapProperty("allMethods", VmClass::getAllMethodMirrors);

    typeAliasFactory
        .addTypedProperty(
            "location", alias -> sourceLocationFactory.create(alias.getHeaderSection()))
        .addProperty(
            "docComment", alias -> VmNull.lift(VmUtils.exportDocComment(alias.getDocComment())))
        .addListProperty("annotations", alias -> VmList.create(alias.getAnnotations()))
        .addSetProperty("modifiers", VmTypeAlias::getModifierMirrors)
        .addStringProperty("name", VmTypeAlias::getSimpleName)
        .addProperty("reflectee", Property.identity())
        .addListProperty("typeParameters", VmTypeAlias::getTypeParameterMirrors)
        .addTypedProperty("enclosingDeclaration", VmTypeAlias::getModuleMirror)
        .addTypedProperty("referent", VmTypeAlias::getTypeMirror);

    propertyFactory
        .addTypedProperty(
            "location",
            property -> sourceLocationFactory.create(property.getProperty().getHeaderSection()))
        .addProperty(
            "docComment",
            property ->
                VmNull.lift(VmUtils.exportDocComment(property.getProperty().getDocComment())))
        .addListProperty(
            "annotations", property -> VmList.create(property.getProperty().getAnnotations()))
        .addListProperty("allAnnotations", property -> VmList.create(property.getAllAnnotations()))
        .addSetProperty("modifiers", property -> property.getProperty().getModifierMirrors())
        .addSetProperty("allModifiers", ClassProperty.Mirror::getAllModifierMirrors)
        .addStringProperty("name", property -> property.getProperty().getName().toString())
        .addTypedProperty("type", property -> property.getProperty().getTypeMirror())
        .addProperty(
            "defaultValue",
            property ->
                property.getProperty().isAbstract()
                        || property.getProperty().isExternal()
                        || property.getProperty().getInitializer().isUndefined()
                    ? VmNull.withoutDefault()
                    :
                    // get default from prototype because it's cached there
                    VmUtils.readMember(
                        property.getProperty().getOwner(), property.getProperty().getName()));

    methodFactory
        .addTypedProperty(
            "location", method -> sourceLocationFactory.create(method.getHeaderSection()))
        .addProperty(
            "docComment", method -> VmNull.lift(VmUtils.exportDocComment(method.getDocComment())))
        .addListProperty("annotations", method -> VmList.create(method.getAnnotations()))
        .addSetProperty("modifiers", ClassMethod::getModifierMirrors)
        .addListProperty("typeParameters", ClassMethod::getTypeParameterMirrors)
        .addStringProperty("name", method -> method.getName().toString())
        .addMapProperty("parameters", ClassMethod::getParameterMirrors)
        .addTypedProperty("returnType", ClassMethod::getReturnTypeMirror);

    methodParameterFactory
        .addStringProperty("name", Pair::getFirst)
        .addTypedProperty("type", Pair::getSecond);

    typeParameterFactory
        .addStringProperty("name", TypeParameter::getName)
        .addProperty(
            "variance",
            typeParameter ->
                switch (typeParameter.getVariance()) {
                  case COVARIANT -> "out";
                  case CONTRAVARIANT -> "in";
                  default -> VmNull.withoutDefault();
                });

    classTypeFactory
        .addTypedProperty(
            "referent",
            typeNode -> {
              var clazz = typeNode.getVmClass();
              assert clazz != null;
              return clazz.getMirror();
            })
        .addListProperty("typeArguments", TypeNode::getTypeArgumentMirrors);

    typeAliasTypeFactory
        .addTypedProperty(
            "referent",
            typeNode -> {
              var alias = typeNode.getVmTypeAlias();
              assert alias != null;
              return alias.getMirror();
            })
        .addListProperty("typeArguments", TypeNode::getTypeArgumentMirrors);

    declaredTypeFactory
        .addTypedProperty("referent", Pair::getFirst)
        .addListProperty("typeArguments", Pair::getSecond);

    functionTypeFactory
        .addListProperty("parameterTypes", FunctionTypeNode::getParameterTypeMirrors)
        .addTypedProperty("returnType", FunctionTypeNode::getReturnTypeMirror);

    functionTypeFactory2
        .addListProperty("parameterTypes", Pair::getFirst)
        .addTypedProperty("returnType", Pair::getSecond);

    stringLiteralTypeFactory.addStringProperty("value", StringLiteralTypeNode::getLiteral);

    stringLiteralTypeFactory2.addStringProperty("value", Property.identity());

    unionTypeFactory.addListProperty("members", UnionTypeNode::getElementTypeMirrors);

    unionTypeFactory2.addListProperty("members", Property.identity());

    unionOfStringLiteralsTypeFactory.addListProperty(
        "members", UnionOfStringLiteralsTypeNode::getElementTypeMirrors);

    unionOfStringLiteralsTypeFactory2.addListProperty("members", Property.identity());

    nullableTypeFactory.addTypedProperty("member", NullableTypeNode::getElementTypeMirror);

    nullableTypeFactory2.addTypedProperty("member", Property.identity());

    typeVariableFactory.addTypedProperty("referent", TypeVariableNode::getTypeParameterMirror);

    typeVariableFactory2.addTypedProperty("referent", Property.identity());

    sourceLocationFactory
        .addIntProperty("line", SourceSection::getStartLine)
        .addIntProperty("column", SourceSection::getStartColumn)
        .addStringProperty(
            "displayUri",
            section -> VmUtils.getDisplayUri(section, VmContext.get(null).getFrameTransformer()));
  }
}
