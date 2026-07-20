/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import java.net.URI;

public final class SyntaxModule extends StdLibModule {
  private static final VmTyped instance = VmUtils.createEmptyModule();

  static {
    loadModule(URI.create("pkl:syntax"), instance);
  }

  public static VmTyped getModule() {
    return instance;
  }

  public static VmClass getNodeClass() {
    return NodeClass.instance;
  }

  public static VmClass getSpanClass() {
    return SpanClass.instance;
  }

  public static VmClass getModuleNodeClass() {
    return ModuleNodeClass.instance;
  }

  public static VmClass getModuleDeclarationNodeClass() {
    return ModuleDeclarationNodeClass.instance;
  }

  public static VmClass getImportNodeClass() {
    return ImportNodeClass.instance;
  }

  public static VmClass getIdentifierNodeClass() {
    return IdentifierNodeClass.instance;
  }

  public static VmClass getQualifiedIdentifierNodeClass() {
    return QualifiedIdentifierNodeClass.instance;
  }

  public static VmClass getDocCommentNodeClass() {
    return DocCommentNodeClass.instance;
  }

  public static VmClass getAnnotationNodeClass() {
    return AnnotationNodeClass.instance;
  }

  public static VmClass getClassNodeClass() {
    return ClassNodeClass.instance;
  }

  public static VmClass getTypeAliasNodeClass() {
    return TypeAliasNodeClass.instance;
  }

  public static VmClass getClassBodyNodeClass() {
    return ClassBodyNodeClass.instance;
  }

  public static VmClass getClassPropertyNodeClass() {
    return ClassPropertyNodeClass.instance;
  }

  public static VmClass getClassMethodNodeClass() {
    return ClassMethodNodeClass.instance;
  }

  public static VmClass getObjectBodyNodeClass() {
    return ObjectBodyNodeClass.instance;
  }

  public static VmClass getParameterNodeClass() {
    return ParameterNodeClass.instance;
  }

  public static VmClass getObjectElementNodeClass() {
    return ObjectElementNodeClass.instance;
  }

  public static VmClass getObjectPropertyNodeClass() {
    return ObjectPropertyNodeClass.instance;
  }

  public static VmClass getObjectMethodNodeClass() {
    return ObjectMethodNodeClass.instance;
  }

  public static VmClass getMemberPredicateNodeClass() {
    return MemberPredicateNodeClass.instance;
  }

  public static VmClass getObjectEntryNodeClass() {
    return ObjectEntryNodeClass.instance;
  }

  public static VmClass getObjectSpreadNodeClass() {
    return ObjectSpreadNodeClass.instance;
  }

  public static VmClass getWhenGeneratorNodeClass() {
    return WhenGeneratorNodeClass.instance;
  }

  public static VmClass getForGeneratorNodeClass() {
    return ForGeneratorNodeClass.instance;
  }

  public static VmClass getStringCharsNodeClass() {
    return StringCharsNodeClass.instance;
  }

  public static VmClass getStringEscapeNodeClass() {
    return StringEscapeNodeClass.instance;
  }

  public static VmClass getStringNewlineNodeClass() {
    return StringNewlineNodeClass.instance;
  }

  public static VmClass getStringInterpolationNodeClass() {
    return StringInterpolationNodeClass.instance;
  }

  public static VmClass getTypeParameterNodeClass() {
    return TypeParameterNodeClass.instance;
  }

  public static VmClass getUnknownTypeNodeClass() {
    return UnknownTypeNodeClass.instance;
  }

  public static VmClass getNothingTypeNodeClass() {
    return NothingTypeNodeClass.instance;
  }

  public static VmClass getModuleTypeNodeClass() {
    return ModuleTypeNodeClass.instance;
  }

  public static VmClass getDeclaredTypeNodeClass() {
    return DeclaredTypeNodeClass.instance;
  }

  public static VmClass getNullableTypeNodeClass() {
    return NullableTypeNodeClass.instance;
  }

  public static VmClass getUnionTypeNodeClass() {
    return UnionTypeNodeClass.instance;
  }

  public static VmClass getFunctionTypeNodeClass() {
    return FunctionTypeNodeClass.instance;
  }

  public static VmClass getConstrainedTypeNodeClass() {
    return ConstrainedTypeNodeClass.instance;
  }

  public static VmClass getParenthesizedTypeNodeClass() {
    return ParenthesizedTypeNodeClass.instance;
  }

  public static VmClass getStringConstantTypeNodeClass() {
    return StringConstantTypeNodeClass.instance;
  }

  public static VmClass getThisExprNodeClass() {
    return ThisExprNodeClass.instance;
  }

  public static VmClass getOuterExprNodeClass() {
    return OuterExprNodeClass.instance;
  }

  public static VmClass getModuleExprNodeClass() {
    return ModuleExprNodeClass.instance;
  }

  public static VmClass getNullLiteralExprNodeClass() {
    return NullLiteralExprNodeClass.instance;
  }

  public static VmClass getBoolLiteralExprNodeClass() {
    return BoolLiteralExprNodeClass.instance;
  }

  public static VmClass getIntLiteralExprNodeClass() {
    return IntLiteralExprNodeClass.instance;
  }

  public static VmClass getFloatLiteralExprNodeClass() {
    return FloatLiteralExprNodeClass.instance;
  }

  public static VmClass getSingleLineStringLiteralExprNodeClass() {
    return SingleLineStringLiteralExprNodeClass.instance;
  }

  public static VmClass getMultiLineStringLiteralExprNodeClass() {
    return MultiLineStringLiteralExprNodeClass.instance;
  }

  public static VmClass getUnqualifiedAccessExprNodeClass() {
    return UnqualifiedAccessExprNodeClass.instance;
  }

  public static VmClass getQualifiedAccessExprNodeClass() {
    return QualifiedAccessExprNodeClass.instance;
  }

  public static VmClass getSubscriptExprNodeClass() {
    return SubscriptExprNodeClass.instance;
  }

  public static VmClass getSuperAccessExprNodeClass() {
    return SuperAccessExprNodeClass.instance;
  }

  public static VmClass getSuperSubscriptExprNodeClass() {
    return SuperSubscriptExprNodeClass.instance;
  }

  public static VmClass getIfExprNodeClass() {
    return IfExprNodeClass.instance;
  }

  public static VmClass getLetExprNodeClass() {
    return LetExprNodeClass.instance;
  }

  public static VmClass getThrowExprNodeClass() {
    return ThrowExprNodeClass.instance;
  }

  public static VmClass getTraceExprNodeClass() {
    return TraceExprNodeClass.instance;
  }

  public static VmClass getImportExprNodeClass() {
    return ImportExprNodeClass.instance;
  }

  public static VmClass getReadExprNodeClass() {
    return ReadExprNodeClass.instance;
  }

  public static VmClass getNewExprNodeClass() {
    return NewExprNodeClass.instance;
  }

  public static VmClass getAmendsExprNodeClass() {
    return AmendsExprNodeClass.instance;
  }

  public static VmClass getBinaryOpExprNodeClass() {
    return BinaryOpExprNodeClass.instance;
  }

  public static VmClass getUnaryMinusExprNodeClass() {
    return UnaryMinusExprNodeClass.instance;
  }

  public static VmClass getLogicalNotExprNodeClass() {
    return LogicalNotExprNodeClass.instance;
  }

  public static VmClass getNonNullExprNodeClass() {
    return NonNullExprNodeClass.instance;
  }

  public static VmClass getFunctionLiteralExprNodeClass() {
    return FunctionLiteralExprNodeClass.instance;
  }

  public static VmClass getParenthesizedExprNodeClass() {
    return ParenthesizedExprNodeClass.instance;
  }

  private static final class NodeClass {
    static final VmClass instance = loadClass("Node");
  }

  private static final class SpanClass {
    static final VmClass instance = loadClass("Span");
  }

  private static final class ModuleNodeClass {
    static final VmClass instance = loadClass("ModuleNode");
  }

  private static final class ModuleDeclarationNodeClass {
    static final VmClass instance = loadClass("ModuleDeclarationNode");
  }

  private static final class ImportNodeClass {
    static final VmClass instance = loadClass("ImportNode");
  }

  private static final class IdentifierNodeClass {
    static final VmClass instance = loadClass("IdentifierNode");
  }

  private static final class QualifiedIdentifierNodeClass {
    static final VmClass instance = loadClass("QualifiedIdentifierNode");
  }

  private static final class DocCommentNodeClass {
    static final VmClass instance = loadClass("DocCommentNode");
  }

  private static final class AnnotationNodeClass {
    static final VmClass instance = loadClass("AnnotationNode");
  }

  private static final class ClassNodeClass {
    static final VmClass instance = loadClass("ClassNode");
  }

  private static final class TypeAliasNodeClass {
    static final VmClass instance = loadClass("TypeAliasNode");
  }

  private static final class ClassBodyNodeClass {
    static final VmClass instance = loadClass("ClassBodyNode");
  }

  private static final class ClassPropertyNodeClass {
    static final VmClass instance = loadClass("ClassPropertyNode");
  }

  private static final class ClassMethodNodeClass {
    static final VmClass instance = loadClass("ClassMethodNode");
  }

  private static final class ObjectBodyNodeClass {
    static final VmClass instance = loadClass("ObjectBodyNode");
  }

  private static final class ParameterNodeClass {
    static final VmClass instance = loadClass("ParameterNode");
  }

  private static final class ObjectElementNodeClass {
    static final VmClass instance = loadClass("ObjectElementNode");
  }

  private static final class ObjectPropertyNodeClass {
    static final VmClass instance = loadClass("ObjectPropertyNode");
  }

  private static final class ObjectMethodNodeClass {
    static final VmClass instance = loadClass("ObjectMethodNode");
  }

  private static final class MemberPredicateNodeClass {
    static final VmClass instance = loadClass("MemberPredicateNode");
  }

  private static final class ObjectEntryNodeClass {
    static final VmClass instance = loadClass("ObjectEntryNode");
  }

  private static final class ObjectSpreadNodeClass {
    static final VmClass instance = loadClass("ObjectSpreadNode");
  }

  private static final class WhenGeneratorNodeClass {
    static final VmClass instance = loadClass("WhenGeneratorNode");
  }

  private static final class ForGeneratorNodeClass {
    static final VmClass instance = loadClass("ForGeneratorNode");
  }

  private static final class StringCharsNodeClass {
    static final VmClass instance = loadClass("StringCharsNode");
  }

  private static final class StringEscapeNodeClass {
    static final VmClass instance = loadClass("StringEscapeNode");
  }

  private static final class StringNewlineNodeClass {
    static final VmClass instance = loadClass("StringNewlineNode");
  }

  private static final class StringInterpolationNodeClass {
    static final VmClass instance = loadClass("StringInterpolationNode");
  }

  private static final class TypeParameterNodeClass {
    static final VmClass instance = loadClass("TypeParameterNode");
  }

  private static final class UnknownTypeNodeClass {
    static final VmClass instance = loadClass("UnknownTypeNode");
  }

  private static final class NothingTypeNodeClass {
    static final VmClass instance = loadClass("NothingTypeNode");
  }

  private static final class ModuleTypeNodeClass {
    static final VmClass instance = loadClass("ModuleTypeNode");
  }

  private static final class DeclaredTypeNodeClass {
    static final VmClass instance = loadClass("DeclaredTypeNode");
  }

  private static final class NullableTypeNodeClass {
    static final VmClass instance = loadClass("NullableTypeNode");
  }

  private static final class UnionTypeNodeClass {
    static final VmClass instance = loadClass("UnionTypeNode");
  }

  private static final class FunctionTypeNodeClass {
    static final VmClass instance = loadClass("FunctionTypeNode");
  }

  private static final class ConstrainedTypeNodeClass {
    static final VmClass instance = loadClass("ConstrainedTypeNode");
  }

  private static final class ParenthesizedTypeNodeClass {
    static final VmClass instance = loadClass("ParenthesizedTypeNode");
  }

  private static final class StringConstantTypeNodeClass {
    static final VmClass instance = loadClass("StringConstantTypeNode");
  }

  private static final class ThisExprNodeClass {
    static final VmClass instance = loadClass("ThisExprNode");
  }

  private static final class OuterExprNodeClass {
    static final VmClass instance = loadClass("OuterExprNode");
  }

  private static final class ModuleExprNodeClass {
    static final VmClass instance = loadClass("ModuleExprNode");
  }

  private static final class NullLiteralExprNodeClass {
    static final VmClass instance = loadClass("NullLiteralExprNode");
  }

  private static final class BoolLiteralExprNodeClass {
    static final VmClass instance = loadClass("BoolLiteralExprNode");
  }

  private static final class IntLiteralExprNodeClass {
    static final VmClass instance = loadClass("IntLiteralExprNode");
  }

  private static final class FloatLiteralExprNodeClass {
    static final VmClass instance = loadClass("FloatLiteralExprNode");
  }

  private static final class SingleLineStringLiteralExprNodeClass {
    static final VmClass instance = loadClass("SingleLineStringLiteralExprNode");
  }

  private static final class MultiLineStringLiteralExprNodeClass {
    static final VmClass instance = loadClass("MultiLineStringLiteralExprNode");
  }

  private static final class UnqualifiedAccessExprNodeClass {
    static final VmClass instance = loadClass("UnqualifiedAccessExprNode");
  }

  private static final class QualifiedAccessExprNodeClass {
    static final VmClass instance = loadClass("QualifiedAccessExprNode");
  }

  private static final class SubscriptExprNodeClass {
    static final VmClass instance = loadClass("SubscriptExprNode");
  }

  private static final class SuperAccessExprNodeClass {
    static final VmClass instance = loadClass("SuperAccessExprNode");
  }

  private static final class SuperSubscriptExprNodeClass {
    static final VmClass instance = loadClass("SuperSubscriptExprNode");
  }

  private static final class IfExprNodeClass {
    static final VmClass instance = loadClass("IfExprNode");
  }

  private static final class LetExprNodeClass {
    static final VmClass instance = loadClass("LetExprNode");
  }

  private static final class ThrowExprNodeClass {
    static final VmClass instance = loadClass("ThrowExprNode");
  }

  private static final class TraceExprNodeClass {
    static final VmClass instance = loadClass("TraceExprNode");
  }

  private static final class ImportExprNodeClass {
    static final VmClass instance = loadClass("ImportExprNode");
  }

  private static final class ReadExprNodeClass {
    static final VmClass instance = loadClass("ReadExprNode");
  }

  private static final class NewExprNodeClass {
    static final VmClass instance = loadClass("NewExprNode");
  }

  private static final class AmendsExprNodeClass {
    static final VmClass instance = loadClass("AmendsExprNode");
  }

  private static final class BinaryOpExprNodeClass {
    static final VmClass instance = loadClass("BinaryOpExprNode");
  }

  private static final class UnaryMinusExprNodeClass {
    static final VmClass instance = loadClass("UnaryMinusExprNode");
  }

  private static final class LogicalNotExprNodeClass {
    static final VmClass instance = loadClass("LogicalNotExprNode");
  }

  private static final class NonNullExprNodeClass {
    static final VmClass instance = loadClass("NonNullExprNode");
  }

  private static final class FunctionLiteralExprNodeClass {
    static final VmClass instance = loadClass("FunctionLiteralExprNode");
  }

  private static final class ParenthesizedExprNodeClass {
    static final VmClass instance = loadClass("ParenthesizedExprNode");
  }

  @CompilerDirectives.TruffleBoundary
  private static VmClass loadClass(String className) {
    var theModule = getModule();
    return (VmClass) VmUtils.readMember(theModule, Identifier.get(className));
  }
}
