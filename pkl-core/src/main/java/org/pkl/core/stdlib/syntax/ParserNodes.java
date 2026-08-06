/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.stdlib.syntax;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.SyntaxModule;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmListing;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.VmObjectFactory;
import org.pkl.core.stdlib.syntax.SyntaxNodes.GenericNodeData;
import org.pkl.core.stdlib.syntax.SyntaxNodes.SpanData;
import org.pkl.parser.GenericParser;
import org.pkl.parser.GenericParserError;
import org.pkl.parser.syntax.generic.Node;
import org.pkl.parser.syntax.generic.NodeType;

public class ParserNodes {
  private ParserNodes() {}

  private static final VmObjectFactory<GenericNodeData> genericNodeFactory =
      new VmObjectFactory<GenericNodeData>(SyntaxModule::getGenericNodeClass)
          .addStringProperty("type", nd -> nd.node.type.name().toLowerCase(Locale.ROOT))
          .addListProperty("children", nd -> nd.childrenVm)
          .addProperty("parent", nd -> VmNull.lift(nd.parentVm))
          .addProperty(
              "text",
              nd ->
                  nd.node.children.isEmpty() || nd.node.type == NodeType.STRING_CHARS
                      ? nd.node.text(nd.source)
                      : VmNull.withoutDefault())
          .addProperty("span", nd -> VmNull.lift(nd.spanVm));

  private static final VmObjectFactory<VmTyped> identifierNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getIdentifierNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addStringProperty("text", ParserNodes::identifierText);

  private static VmObjectFactory<VmTyped> genericNodeOnlyFactory(Supplier<VmClass> classSupplier) {
    return new VmObjectFactory<VmTyped>(classSupplier).addProperty("genericNode", vm -> vm);
  }

  private static final VmObjectFactory<VmTyped> qualifiedIdentifierNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getQualifiedIdentifierNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addListingProperty("identifiers", ParserNodes::qualifiedIdentifierIdentifiers)
          .addStringProperty("value", ParserNodes::qualifiedIdentifierValue);
  private static final VmObjectFactory<VmTyped> docCommentNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getDocCommentNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addStringProperty("value", ParserNodes::docCommentValue);
  private static final VmObjectFactory<VmTyped> annotationNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getAnnotationNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("type", ParserNodes::annotationType)
          .addProperty("body", ParserNodes::annotationBody);
  private static final VmObjectFactory<VmTyped> typeParameterNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getTypeParameterNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addListingProperty("modifiers", ParserNodes::typeParameterModifiers)
          .addTypedProperty("identifier", ParserNodes::identifierNodeOf);
  private static final VmObjectFactory<VmTyped> objectBodyNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getObjectBodyNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addListingProperty("parameters", ParserNodes::objectBodyParameters)
          .addListingProperty("members", ParserNodes::objectBodyMembers);
  private static final VmObjectFactory<VmTyped> parameterNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getParameterNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addBooleanProperty("isBlankIdentifier", ParserNodes::parameterIsBlankIdentifier)
          .addProperty("identifier", ParserNodes::parameterIdentifier)
          .addProperty("typeAnnotation", ParserNodes::parameterTypeAnnotation);

  private static final VmObjectFactory<VmTyped> objectPropertyNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getObjectPropertyNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addListingProperty("modifiers", ParserNodes::objectPropertyModifiers)
          .addTypedProperty("identifier", ParserNodes::objectPropertyIdentifier)
          .addProperty("typeAnnotation", ParserNodes::objectPropertyTypeAnnotation)
          .addProperty("value", ParserNodes::objectPropertyValue)
          .addListingProperty("objectBodies", ParserNodes::objectPropertyObjectBodies);
  private static final VmObjectFactory<VmTyped> objectMethodNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getObjectMethodNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addListingProperty("modifiers", ParserNodes::classMethodModifiers)
          .addTypedProperty("identifier", ParserNodes::classMethodIdentifier)
          .addListingProperty("typeParameters", ParserNodes::classMethodTypeParameters)
          .addListingProperty("parameters", ParserNodes::classMethodParameters)
          .addProperty("returnType", ParserNodes::classMethodReturnType)
          .addTypedProperty("body", ParserNodes::objectMethodBody);
  private static final VmObjectFactory<VmTyped> memberPredicateNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getMemberPredicateNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("condition", ParserNodes::memberPredicateCondition)
          .addProperty("value", ParserNodes::memberPredicateValue)
          .addListingProperty("objectBodies", ParserNodes::memberPredicateObjectBodies);
  private static final VmObjectFactory<VmTyped> objectEntryNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getObjectEntryNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("key", ParserNodes::objectEntryKey)
          .addProperty("value", ParserNodes::objectEntryValue)
          .addListingProperty("objectBodies", ParserNodes::objectEntryObjectBodies);
  private static final VmObjectFactory<VmTyped> objectSpreadNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getObjectSpreadNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addStringProperty("keyword", ParserNodes::objectSpreadKeyword)
          .addTypedProperty("expression", ParserNodes::soleExpr);
  private static final VmObjectFactory<VmTyped> whenGeneratorNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getWhenGeneratorNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("condition", ParserNodes::whenCondition)
          .addTypedProperty("thenBody", ParserNodes::whenThenBody)
          .addProperty("elseBody", ParserNodes::whenElseBody);
  private static final VmObjectFactory<VmTyped> forGeneratorNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getForGeneratorNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addProperty("keyParameter", ParserNodes::forKeyParameter)
          .addTypedProperty("valueParameter", ParserNodes::forValueParameter)
          .addTypedProperty("iterable", ParserNodes::forIterable)
          .addTypedProperty("body", ParserNodes::forBody);

  private static final VmObjectFactory<VmTyped> unknownTypeNodeFactory =
      genericNodeOnlyFactory(SyntaxModule::getUnknownTypeNodeClass);
  private static final VmObjectFactory<VmTyped> nothingTypeNodeFactory =
      genericNodeOnlyFactory(SyntaxModule::getNothingTypeNodeClass);
  private static final VmObjectFactory<VmTyped> moduleTypeNodeFactory =
      genericNodeOnlyFactory(SyntaxModule::getModuleTypeNodeClass);
  private static final VmObjectFactory<VmTyped> declaredTypeNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getDeclaredTypeNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("name", ParserNodes::declaredTypeName)
          .addListingProperty("typeArguments", ParserNodes::declaredTypeArguments);
  private static final VmObjectFactory<VmTyped> nullableTypeNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getNullableTypeNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("baseType", ParserNodes::nullableTypeBaseType);
  private static final VmObjectFactory<VmTyped> unionTypeNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getUnionTypeNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addListingProperty("members", ParserNodes::unionTypeMembers);
  private static final VmObjectFactory<VmTyped> functionTypeNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getFunctionTypeNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addListingProperty("parameterTypes", ParserNodes::functionTypeParameterTypes)
          .addTypedProperty("returnType", ParserNodes::functionTypeReturnType);
  private static final VmObjectFactory<VmTyped> constrainedTypeNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getConstrainedTypeNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("baseType", ParserNodes::constrainedTypeBaseType)
          .addListingProperty("constraints", ParserNodes::constrainedTypeConstraints);
  private static final VmObjectFactory<VmTyped> parenthesizedTypeNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getParenthesizedTypeNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("type", ParserNodes::parenthesizedTypeType);
  private static final VmObjectFactory<VmTyped> stringLiteralTypeNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getStringLiteralTypeNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addStringProperty("value", ParserNodes::stringLiteralTypeValue);

  private static final VmObjectFactory<VmTyped> thisExprNodeFactory =
      genericNodeOnlyFactory(SyntaxModule::getThisExprNodeClass);
  private static final VmObjectFactory<VmTyped> outerExprNodeFactory =
      genericNodeOnlyFactory(SyntaxModule::getOuterExprNodeClass);
  private static final VmObjectFactory<VmTyped> moduleExprNodeFactory =
      genericNodeOnlyFactory(SyntaxModule::getModuleExprNodeClass);
  private static final VmObjectFactory<VmTyped> nullLiteralExprNodeFactory =
      genericNodeOnlyFactory(SyntaxModule::getNullLiteralExprNodeClass);
  private static final VmObjectFactory<VmTyped> booleanLiteralExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getBooleanLiteralExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addBooleanProperty("value", ParserNodes::booleanLiteralValue);
  private static final VmObjectFactory<VmTyped> intLiteralExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getIntLiteralExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addProperty("value", ParserNodes::literalText);
  private static final VmObjectFactory<VmTyped> floatLiteralExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getFloatLiteralExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addProperty("value", ParserNodes::literalText);
  private static final VmObjectFactory<VmTyped> singleLineStringLiteralExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getSingleLineStringLiteralExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addListingProperty("parts", ParserNodes::buildStringParts);
  private static final VmObjectFactory<VmTyped> multiLineStringLiteralExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getMultiLineStringLiteralExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addListingProperty("parts", ParserNodes::buildStringParts);
  private static final VmObjectFactory<VmTyped> unqualifiedAccessExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getUnqualifiedAccessExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("identifier", ParserNodes::identifierNodeOf)
          .addProperty("arguments", ParserNodes::unqualifiedAccessArguments);
  private static final VmObjectFactory<VmTyped> qualifiedAccessExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getQualifiedAccessExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("receiver", ParserNodes::qualifiedAccessReceiver)
          .addBooleanProperty("isNullSafe", ParserNodes::qualifiedAccessIsNullSafe)
          .addTypedProperty("identifier", ParserNodes::qualifiedAccessIdentifier)
          .addProperty("arguments", ParserNodes::qualifiedAccessArguments);
  private static final VmObjectFactory<VmTyped> subscriptExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getSubscriptExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("receiver", ParserNodes::subscriptReceiver)
          .addTypedProperty("index", ParserNodes::subscriptIndex);
  private static final VmObjectFactory<VmTyped> superAccessExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getSuperAccessExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("identifier", ParserNodes::identifierNodeOf)
          .addProperty("arguments", ParserNodes::argumentsOrNull);
  private static final VmObjectFactory<VmTyped> superSubscriptExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getSuperSubscriptExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("index", ParserNodes::soleExpr);
  private static final VmObjectFactory<VmTyped> ifExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getIfExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("condition", ParserNodes::ifCondition)
          .addTypedProperty("thenExpr", ParserNodes::ifThenExpr)
          .addTypedProperty("elseExpr", ParserNodes::ifElseExpr);
  private static final VmObjectFactory<VmTyped> letExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getLetExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("parameter", ParserNodes::letParameter)
          .addTypedProperty("bindingValue", ParserNodes::letBindingValue)
          .addTypedProperty("body", ParserNodes::letBody);
  private static final VmObjectFactory<VmTyped> throwExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getThrowExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("expression", ParserNodes::soleExpr);
  private static final VmObjectFactory<VmTyped> traceExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getTraceExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("expression", ParserNodes::soleExpr);
  private static final VmObjectFactory<VmTyped> importExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getImportExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addStringProperty("keyword", ParserNodes::importKeyword)
          .addStringProperty("uri", ParserNodes::importUri);
  private static final VmObjectFactory<VmTyped> readExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getReadExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addStringProperty("keyword", ParserNodes::readKeyword)
          .addTypedProperty("expression", ParserNodes::soleExpr);
  private static final VmObjectFactory<VmTyped> newExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getNewExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addProperty("type", ParserNodes::newExprType)
          .addTypedProperty("body", ParserNodes::newExprBody);
  private static final VmObjectFactory<VmTyped> amendsExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getAmendsExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("parentExpr", ParserNodes::amendsParentExpr)
          .addTypedProperty("body", ParserNodes::amendsBody);

  private static VmObjectFactory<VmTyped> binaryOpExprNodeFactory(Supplier<VmClass> classSupplier) {
    return new VmObjectFactory<VmTyped>(classSupplier)
        .addProperty("genericNode", vm -> vm)
        .addTypedProperty("left", ParserNodes::binaryOpLeft)
        .addTypedProperty("right", ParserNodes::binaryOpRight);
  }

  private static VmObjectFactory<VmTyped> typeOpExprNodeFactory(Supplier<VmClass> classSupplier) {
    return new VmObjectFactory<VmTyped>(classSupplier)
        .addProperty("genericNode", vm -> vm)
        .addTypedProperty("expression", ParserNodes::binaryOpLeft)
        .addTypedProperty("type", ParserNodes::typeOpType);
  }

  private static final VmObjectFactory<VmTyped> exponentiationExprNodeFactory =
      binaryOpExprNodeFactory(SyntaxModule::getExponentiationExprNodeClass);
  private static final VmObjectFactory<VmTyped> multiplicationExprNodeFactory =
      binaryOpExprNodeFactory(SyntaxModule::getMultiplicationExprNodeClass);
  private static final VmObjectFactory<VmTyped> divisionExprNodeFactory =
      binaryOpExprNodeFactory(SyntaxModule::getDivisionExprNodeClass);
  private static final VmObjectFactory<VmTyped> integerDivisionExprNodeFactory =
      binaryOpExprNodeFactory(SyntaxModule::getIntegerDivisionExprNodeClass);
  private static final VmObjectFactory<VmTyped> remainderExprNodeFactory =
      binaryOpExprNodeFactory(SyntaxModule::getRemainderExprNodeClass);
  private static final VmObjectFactory<VmTyped> additionExprNodeFactory =
      binaryOpExprNodeFactory(SyntaxModule::getAdditionExprNodeClass);
  private static final VmObjectFactory<VmTyped> subtractionExprNodeFactory =
      binaryOpExprNodeFactory(SyntaxModule::getSubtractionExprNodeClass);
  private static final VmObjectFactory<VmTyped> lessThanExprNodeFactory =
      binaryOpExprNodeFactory(SyntaxModule::getLessThanExprNodeClass);
  private static final VmObjectFactory<VmTyped> lessThanOrEqualExprNodeFactory =
      binaryOpExprNodeFactory(SyntaxModule::getLessThanOrEqualExprNodeClass);
  private static final VmObjectFactory<VmTyped> greaterThanExprNodeFactory =
      binaryOpExprNodeFactory(SyntaxModule::getGreaterThanExprNodeClass);
  private static final VmObjectFactory<VmTyped> greaterThanOrEqualExprNodeFactory =
      binaryOpExprNodeFactory(SyntaxModule::getGreaterThanOrEqualExprNodeClass);
  private static final VmObjectFactory<VmTyped> equalExprNodeFactory =
      binaryOpExprNodeFactory(SyntaxModule::getEqualExprNodeClass);
  private static final VmObjectFactory<VmTyped> notEqualExprNodeFactory =
      binaryOpExprNodeFactory(SyntaxModule::getNotEqualExprNodeClass);
  private static final VmObjectFactory<VmTyped> logicalAndExprNodeFactory =
      binaryOpExprNodeFactory(SyntaxModule::getLogicalAndExprNodeClass);
  private static final VmObjectFactory<VmTyped> logicalOrExprNodeFactory =
      binaryOpExprNodeFactory(SyntaxModule::getLogicalOrExprNodeClass);
  private static final VmObjectFactory<VmTyped> pipeExprNodeFactory =
      binaryOpExprNodeFactory(SyntaxModule::getPipeExprNodeClass);
  private static final VmObjectFactory<VmTyped> nullCoalescingExprNodeFactory =
      binaryOpExprNodeFactory(SyntaxModule::getNullCoalescingExprNodeClass);
  private static final VmObjectFactory<VmTyped> typeCheckExprNodeFactory =
      typeOpExprNodeFactory(SyntaxModule::getTypeCheckExprNodeClass);
  private static final VmObjectFactory<VmTyped> typeCastExprNodeFactory =
      typeOpExprNodeFactory(SyntaxModule::getTypeCastExprNodeClass);
  private static final VmObjectFactory<VmTyped> unaryMinusExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getUnaryMinusExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("operand", ParserNodes::soleExpr);
  private static final VmObjectFactory<VmTyped> logicalNotExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getLogicalNotExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("operand", ParserNodes::soleExpr);
  private static final VmObjectFactory<VmTyped> nonNullExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getNonNullExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("operand", ParserNodes::soleExpr);
  private static final VmObjectFactory<VmTyped> functionLiteralExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getFunctionLiteralExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addListingProperty("parameters", ParserNodes::functionLiteralParameters)
          .addTypedProperty("body", ParserNodes::functionLiteralBody);
  private static final VmObjectFactory<VmTyped> parenthesizedExprNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getParenthesizedExprNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("expression", ParserNodes::parenthesizedExpression);

  // String-part factories, produced by `buildStringParts`. `StringPartNode` is not a `Node`,
  // but it also carries a hidden `genericNode`, so the same property shape applies.
  private static final VmObjectFactory<VmTyped> stringCharsNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getStringCharsNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addStringProperty("value", ParserNodes::literalText);
  private static final VmObjectFactory<VmTyped> stringEscapeNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getStringEscapeNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addStringProperty("value", ParserNodes::literalText);
  private static final VmObjectFactory<VmTyped> stringNewlineNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getStringNewlineNodeClass)
          .addProperty("genericNode", vm -> vm);
  private static final VmObjectFactory<VmTyped> stringInterpolationNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getStringInterpolationNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addTypedProperty("expression", ParserNodes::wrapExpr);

  private static final VmObjectFactory<VmTyped> importNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getImportNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addStringProperty("keyword", ParserNodes::importKeyword)
          .addStringProperty("uri", ParserNodes::importUri)
          .addProperty("alias", ParserNodes::importAlias);

  private static final VmObjectFactory<VmTyped> moduleDeclarationNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getModuleDeclarationNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addProperty("docComment", ParserNodes::docCommentOf)
          .addListingProperty("annotations", ParserNodes::annotationsOf)
          .addListingProperty("modifiers", ParserNodes::moduleDeclModifiers)
          .addProperty("name", ParserNodes::moduleDeclName)
          .addProperty("extendsOrAmendsClause", ParserNodes::moduleDeclExtendsOrAmendsClause);

  private static final VmObjectFactory<VmTyped> extendsOrAmendsClauseNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getExtendsOrAmendsClauseNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addStringProperty("keyword", ParserNodes::extendsOrAmendsClauseKeyword)
          .addStringProperty("uri", ParserNodes::stringCharsOf);

  private static final VmObjectFactory<VmTyped> classNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getClassNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addProperty("docComment", ParserNodes::docCommentOf)
          .addListingProperty("annotations", ParserNodes::annotationsOf)
          .addListingProperty("modifiers", ParserNodes::classModifiers)
          .addTypedProperty("identifier", ParserNodes::classIdentifier)
          .addListingProperty("typeParameters", ParserNodes::classTypeParameters)
          .addProperty("superType", ParserNodes::classSuperType)
          .addProperty("body", ParserNodes::classBody);

  private static final VmObjectFactory<VmTyped> typeAliasNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getTypeAliasNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addProperty("docComment", ParserNodes::docCommentOf)
          .addListingProperty("annotations", ParserNodes::annotationsOf)
          .addListingProperty("modifiers", ParserNodes::typeAliasModifiers)
          .addTypedProperty("identifier", ParserNodes::typeAliasIdentifier)
          .addListingProperty("typeParameters", ParserNodes::typeAliasTypeParameters)
          .addTypedProperty("type", ParserNodes::typeAliasType);

  private static final VmObjectFactory<VmTyped> classPropertyNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getClassPropertyNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addProperty("docComment", ParserNodes::docCommentOf)
          .addListingProperty("annotations", ParserNodes::annotationsOf)
          .addListingProperty("modifiers", ParserNodes::classPropertyModifiers)
          .addTypedProperty("identifier", ParserNodes::classPropertyIdentifier)
          .addProperty("typeAnnotation", ParserNodes::classPropertyTypeAnnotation)
          .addProperty("value", ParserNodes::classPropertyValue)
          .addListingProperty("objectBodies", ParserNodes::classPropertyObjectBodies);

  private static final VmObjectFactory<VmTyped> classMethodNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getClassMethodNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addProperty("docComment", ParserNodes::docCommentOf)
          .addListingProperty("annotations", ParserNodes::annotationsOf)
          .addListingProperty("modifiers", ParserNodes::classMethodModifiers)
          .addTypedProperty("identifier", ParserNodes::classMethodIdentifier)
          .addListingProperty("typeParameters", ParserNodes::classMethodTypeParameters)
          .addListingProperty("parameters", ParserNodes::classMethodParameters)
          .addProperty("returnType", ParserNodes::classMethodReturnType)
          .addProperty("body", ParserNodes::classMethodBody);

  private static final VmObjectFactory<VmTyped> classBodyNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getClassBodyNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addListingProperty("members", ParserNodes::classBodyMembers);

  private static final VmObjectFactory<VmTyped> moduleNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getModuleNodeClass)
          .addProperty("genericNode", vm -> vm)
          .addProperty("declaration", ParserNodes::moduleDeclaration)
          .addListingProperty("imports", ParserNodes::moduleImports)
          .addListingProperty("members", ParserNodes::memberChildren);

  private static Object moduleDeclaration(VmTyped moduleVm) {
    var declVm = findChildVm(moduleVm, NodeType.MODULE_DECLARATION);
    return declVm == null ? VmNull.withoutDefault() : moduleDeclarationNodeFactory.create(declVm);
  }

  private static Object docCommentOf(VmTyped ownerVm) {
    var dc = findChildVm(ownerVm, NodeType.DOC_COMMENT);
    return dc == null ? VmNull.withoutDefault() : docCommentNodeFactory.create(dc);
  }

  private static VmListing annotationsOf(VmTyped ownerVm) {
    return wrapAll(findChildrenVm(ownerVm, NodeType.ANNOTATION), annotationNodeFactory);
  }

  private static VmTyped annotationType(VmTyped annotationVm) {
    var type = findTypeChildVm(annotationVm);
    if (type == null) {
      throw new VmExceptionBuilder().bug("An annotation always has a type.").build();
    }
    return wrapType(type);
  }

  private static Object annotationBody(VmTyped annotationVm) {
    var body = findChildVm(annotationVm, NodeType.OBJECT_BODY);
    return body == null ? VmNull.withoutDefault() : objectBodyNodeFactory.create(body);
  }

  private static String docCommentValue(VmTyped docCommentVm) {
    var lineVms = findChildrenVm(docCommentVm, NodeType.DOC_COMMENT_LINE);
    var builder = new StringBuilder();
    for (var i = 0; i < lineVms.size(); i++) {
      if (i > 0) {
        builder.append('\n');
      }
      var data = (GenericNodeData) lineVms.get(i).getExtraStorage();
      var text = data.node.text(data.source);
      if (text.startsWith("/// ")) {
        builder.append(text, 4, text.length());
      } else if (text.startsWith("///")) {
        builder.append(text, 3, text.length());
      } else {
        builder.append(text);
      }
    }
    return builder.toString();
  }

  private static VmTyped declaredTypeName(VmTyped typeVm) {
    var name = findChildVm(typeVm, NodeType.QUALIFIED_IDENTIFIER);
    if (name == null) {
      throw new VmExceptionBuilder()
          .bug("A declared type always has a qualified identifier.")
          .build();
    }
    return qualifiedIdentifierNodeFactory.create(name);
  }

  private static VmListing declaredTypeArguments(VmTyped typeVm) {
    var list = findChildVm(typeVm, NodeType.TYPE_ARGUMENT_LIST);
    if (list == null) {
      return VmListing.empty();
    }
    var elems = findChildVm(list, NodeType.TYPE_ARGUMENT_LIST_ELEMENTS);
    if (elems == null) {
      return VmListing.empty();
    }
    return wrapTypes(findTypeChildrenVm(elems));
  }

  private static VmTyped nullableTypeBaseType(VmTyped typeVm) {
    return wrapType(requireTypeChild(typeVm));
  }

  private static VmListing unionTypeMembers(VmTyped typeVm) {
    return wrapTypes(findTypeChildrenVm(typeVm));
  }

  private static VmListing functionTypeParameterTypes(VmTyped typeVm) {
    var params = findChildVm(typeVm, NodeType.FUNCTION_TYPE_PARAMETERS);
    if (params == null) {
      return VmListing.empty();
    }
    var elems = findChildVm(params, NodeType.PARENTHESIZED_TYPE_ELEMENTS);
    if (elems == null) {
      return VmListing.empty();
    }
    return wrapTypes(findTypeChildrenVm(elems));
  }

  private static VmTyped functionTypeReturnType(VmTyped typeVm) {
    var types = findTypeChildrenVm(typeVm);
    if (types.isEmpty()) {
      throw new VmExceptionBuilder().bug("A function type always has a return type.").build();
    }
    return wrapType(types.get(types.size() - 1));
  }

  private static VmTyped constrainedTypeBaseType(VmTyped typeVm) {
    return wrapType(requireTypeChild(typeVm));
  }

  private static VmListing constrainedTypeConstraints(VmTyped typeVm) {
    var constraint = findChildVm(typeVm, NodeType.CONSTRAINED_TYPE_CONSTRAINT);
    if (constraint == null) {
      return VmListing.empty();
    }
    var elems = findChildVm(constraint, NodeType.CONSTRAINED_TYPE_ELEMENTS);
    if (elems == null) {
      return VmListing.empty();
    }
    return wrapExprs(findExprChildrenVm(elems));
  }

  private static VmTyped parenthesizedTypeType(VmTyped typeVm) {
    var elems = findChildVm(typeVm, NodeType.PARENTHESIZED_TYPE_ELEMENTS);
    var type = elems == null ? null : findTypeChildVm(elems);
    if (type == null) {
      throw new VmExceptionBuilder().bug("A parenthesized type always has an inner type.").build();
    }
    return wrapType(type);
  }

  private static String stringLiteralTypeValue(VmTyped typeVm) {
    var data = (GenericNodeData) typeVm.getExtraStorage();
    return extractStringChars(data.node, data.source);
  }

  private static VmTyped requireTypeChild(VmTyped genericVm) {
    var type = findTypeChildVm(genericVm);
    if (type == null) {
      throw new VmExceptionBuilder().bug("Expected a type-node child.").build();
    }
    return type;
  }

  private static String literalText(VmTyped exprVm) {
    var text = nodeText((GenericNodeData) exprVm.getExtraStorage());
    return text == null ? "" : text;
  }

  private static boolean booleanLiteralValue(VmTyped exprVm) {
    return "true".equals(nodeText((GenericNodeData) exprVm.getExtraStorage()));
  }

  private static Object unqualifiedAccessArguments(VmTyped exprVm) {
    return argumentsOrNull(exprVm);
  }

  private static VmTyped qualifiedAccessReceiver(VmTyped exprVm) {
    return wrapExpr(requireExprChild(exprVm));
  }

  private static boolean qualifiedAccessIsNullSafe(VmTyped exprVm) {
    var op = findChildVm(exprVm, NodeType.OPERATOR);
    if (op == null) {
      return false;
    }
    var data = (GenericNodeData) op.getExtraStorage();
    return "?.".equals(data.node.text(data.source));
  }

  private static VmTyped qualifiedAccessIdentifier(VmTyped exprVm) {
    return identifierNodeOf(lastChildVm(exprVm, NodeType.UNQUALIFIED_ACCESS_EXPR));
  }

  private static Object qualifiedAccessArguments(VmTyped exprVm) {
    var member = lastChildVm(exprVm, NodeType.UNQUALIFIED_ACCESS_EXPR);
    return member == null ? VmNull.withoutDefault() : argumentsOrNull(member);
  }

  private static VmTyped subscriptReceiver(VmTyped exprVm) {
    return wrapExpr(findExprChildrenVm(exprVm).get(0));
  }

  private static VmTyped subscriptIndex(VmTyped exprVm) {
    return wrapExpr(findExprChildrenVm(exprVm).get(1));
  }

  private static VmTyped ifCondition(VmTyped exprVm) {
    var header = requireChild(exprVm, NodeType.IF_HEADER);
    var condition = requireChild(header, NodeType.IF_CONDITION);
    var conditionExpr = requireChild(condition, NodeType.IF_CONDITION_EXPR);
    return wrapExpr(requireExprChild(conditionExpr));
  }

  private static VmTyped ifThenExpr(VmTyped exprVm) {
    return wrapExpr(requireExprChild(requireChild(exprVm, NodeType.IF_THEN_EXPR)));
  }

  private static VmTyped ifElseExpr(VmTyped exprVm) {
    return wrapExpr(requireExprChild(requireChild(exprVm, NodeType.IF_ELSE_EXPR)));
  }

  private static VmTyped letParamNode(VmTyped exprVm) {
    return requireChild(
        requireChild(exprVm, NodeType.LET_PARAMETER_DEFINITION), NodeType.LET_PARAMETER);
  }

  private static VmTyped letParameter(VmTyped exprVm) {
    return parameterNodeFactory.create(requireChild(letParamNode(exprVm), NodeType.PARAMETER));
  }

  private static VmTyped letBindingValue(VmTyped exprVm) {
    return wrapExpr(requireExprChild(letParamNode(exprVm)));
  }

  private static VmTyped letBody(VmTyped exprVm) {
    return wrapExpr(requireExprChild(exprVm));
  }

  // The sole expression child of `exprVm` (throw/trace/unary-minus/logical-not/non-null operand).
  private static VmTyped soleExpr(VmTyped exprVm) {
    return wrapExpr(requireExprChild(exprVm));
  }

  private static String readKeyword(VmTyped exprVm) {
    return firstTerminalText(exprVm, "read");
  }

  private static Object newExprType(VmTyped exprVm) {
    var header = requireChild(exprVm, NodeType.NEW_HEADER);
    var type = findTypeChildVm(header);
    return type == null ? VmNull.withoutDefault() : wrapType(type);
  }

  private static VmTyped newExprBody(VmTyped exprVm) {
    return objectBodyNodeFactory.create(requireChild(exprVm, NodeType.OBJECT_BODY));
  }

  private static VmTyped amendsParentExpr(VmTyped exprVm) {
    return wrapExpr(requireExprChild(exprVm));
  }

  private static VmTyped amendsBody(VmTyped exprVm) {
    return objectBodyNodeFactory.create(requireChild(exprVm, NodeType.OBJECT_BODY));
  }

  private static String binaryOpOperator(VmTyped exprVm) {
    var op = findChildVm(exprVm, NodeType.OPERATOR);
    if (op == null) {
      return "";
    }
    var data = (GenericNodeData) op.getExtraStorage();
    return data.node.text(data.source);
  }

  private static VmTyped binaryOpLeft(VmTyped exprVm) {
    return wrapExpr(findExprChildrenVm(exprVm).get(0));
  }

  private static VmTyped binaryOpRight(VmTyped exprVm) {
    return wrapExpr(findExprChildrenVm(exprVm).get(1));
  }

  private static VmTyped typeOpType(VmTyped exprVm) {
    return wrapType(requireTypeChild(exprVm));
  }

  private static VmObjectFactory<VmTyped> binaryOpFactory(VmTyped exprVm) {
    var operator = binaryOpOperator(exprVm);
    return switch (operator) {
      case "**" -> exponentiationExprNodeFactory;
      case "*" -> multiplicationExprNodeFactory;
      case "/" -> divisionExprNodeFactory;
      case "~/" -> integerDivisionExprNodeFactory;
      case "%" -> remainderExprNodeFactory;
      case "+" -> additionExprNodeFactory;
      case "-" -> subtractionExprNodeFactory;
      case "<" -> lessThanExprNodeFactory;
      case "<=" -> lessThanOrEqualExprNodeFactory;
      case ">" -> greaterThanExprNodeFactory;
      case ">=" -> greaterThanOrEqualExprNodeFactory;
      case "==" -> equalExprNodeFactory;
      case "!=" -> notEqualExprNodeFactory;
      case "&&" -> logicalAndExprNodeFactory;
      case "||" -> logicalOrExprNodeFactory;
      case "|>" -> pipeExprNodeFactory;
      case "??" -> nullCoalescingExprNodeFactory;
      case "is" -> typeCheckExprNodeFactory;
      case "as" -> typeCastExprNodeFactory;
      default ->
          throw new VmExceptionBuilder().bug("Unexpected binary operator: " + operator).build();
    };
  }

  private static VmListing functionLiteralParameters(VmTyped exprVm) {
    return parametersOf(exprVm);
  }

  private static VmTyped functionLiteralBody(VmTyped exprVm) {
    return wrapExpr(requireExprChild(requireChild(exprVm, NodeType.FUNCTION_LITERAL_BODY)));
  }

  private static VmTyped parenthesizedExpression(VmTyped exprVm) {
    var elems = requireChild(exprVm, NodeType.PARENTHESIZED_EXPR_ELEMENTS);
    return wrapExpr(requireExprChild(elems));
  }

  private static Object argumentsOrNull(VmTyped ownerVm) {
    var argList = findChildVm(ownerVm, NodeType.ARGUMENT_LIST);
    return argList == null ? VmNull.withoutDefault() : argumentsOf(argList);
  }

  private static VmListing argumentsOf(VmTyped argListVm) {
    var elems = findChildVm(argListVm, NodeType.ARGUMENT_LIST_ELEMENTS);
    if (elems == null) {
      return VmListing.empty();
    }
    return wrapExprs(findExprChildrenVm(elems));
  }

  private static VmTyped requireChild(VmTyped genericVm, NodeType type) {
    var child = findChildVm(genericVm, type);
    if (child == null) {
      throw new VmExceptionBuilder().bug("Expected a `" + type + "` child.").build();
    }
    return child;
  }

  private static VmTyped requireExprChild(VmTyped genericVm) {
    var expr = findExprChildVm(genericVm);
    if (expr == null) {
      throw new VmExceptionBuilder().bug("Expected an expression child.").build();
    }
    return expr;
  }

  private static @Nullable VmTyped lastChildVm(VmTyped genericVm, NodeType type) {
    var data = (GenericNodeData) genericVm.getExtraStorage();
    var children = data.node.children;
    VmTyped result = null;
    for (var i = 0; i < children.size(); i++) {
      if (children.get(i).type == type) {
        result = (VmTyped) data.childrenVm.get(i);
      }
    }
    return result;
  }

  private static VmListing objectBodyParameters(VmTyped bodyVm) {
    var paramList = findChildVm(bodyVm, NodeType.OBJECT_PARAMETER_LIST);
    if (paramList == null) {
      return VmListing.empty();
    }
    return wrapAll(findChildrenVm(paramList, NodeType.PARAMETER), parameterNodeFactory);
  }

  private static VmListing objectBodyMembers(VmTyped bodyVm) {
    var memberList = findChildVm(bodyVm, NodeType.OBJECT_MEMBER_LIST);
    if (memberList == null) {
      return VmListing.empty();
    }
    var data = (GenericNodeData) memberList.getExtraStorage();
    var children = data.node.children;
    var result = new ArrayList<>();
    for (var i = 0; i < children.size(); i++) {
      var childVm = (VmTyped) data.childrenVm.get(i);
      var member =
          switch (children.get(i).type) {
            case OBJECT_PROPERTY -> objectPropertyNodeFactory.create(childVm);
            case OBJECT_METHOD -> objectMethodNodeFactory.create(childVm);
            // an element is represented by its expression
            case OBJECT_ELEMENT -> soleExpr(childVm);
            case OBJECT_ENTRY -> objectEntryNodeFactory.create(childVm);
            case OBJECT_SPREAD -> objectSpreadNodeFactory.create(childVm);
            case MEMBER_PREDICATE -> memberPredicateNodeFactory.create(childVm);
            case FOR_GENERATOR -> forGeneratorNodeFactory.create(childVm);
            case WHEN_GENERATOR -> whenGeneratorNodeFactory.create(childVm);
            default -> null;
          };
      if (member != null) {
        result.add(member);
      }
    }
    return listingOf(result.toArray());
  }

  private static @Nullable VmTyped objectPropertyHeaderBegin(VmTyped propertyVm) {
    var header = findChildVm(propertyVm, NodeType.OBJECT_PROPERTY_HEADER);
    return header == null ? null : findChildVm(header, NodeType.OBJECT_PROPERTY_HEADER_BEGIN);
  }

  private static VmListing objectPropertyModifiers(VmTyped propertyVm) {
    var headerBegin = objectPropertyHeaderBegin(propertyVm);
    return headerBegin == null ? VmListing.empty() : modifiersOf(headerBegin);
  }

  private static VmTyped objectPropertyIdentifier(VmTyped propertyVm) {
    return identifierNodeOf(objectPropertyHeaderBegin(propertyVm));
  }

  private static Object objectPropertyTypeAnnotation(VmTyped propertyVm) {
    return typeAnnotationOf(findChildVm(propertyVm, NodeType.OBJECT_PROPERTY_HEADER));
  }

  private static Object objectPropertyValue(VmTyped propertyVm) {
    return exprInChild(propertyVm, NodeType.OBJECT_PROPERTY_BODY);
  }

  private static VmListing objectPropertyObjectBodies(VmTyped propertyVm) {
    return objectBodiesOf(propertyVm);
  }

  private static VmTyped objectMethodBody(VmTyped methodVm) {
    return wrapExpr(requireExprChild(requireChild(methodVm, NodeType.CLASS_METHOD_BODY)));
  }

  private static VmTyped memberPredicateCondition(VmTyped predicateVm) {
    return wrapExpr(findExprChildrenVm(predicateVm).get(0));
  }

  private static Object memberPredicateValue(VmTyped predicateVm) {
    var exprs = findExprChildrenVm(predicateVm);
    return exprs.size() < 2 ? VmNull.withoutDefault() : wrapExpr(exprs.get(1));
  }

  private static VmListing memberPredicateObjectBodies(VmTyped predicateVm) {
    return objectBodiesOf(predicateVm);
  }

  private static VmTyped objectEntryKey(VmTyped entryVm) {
    var header = requireChild(entryVm, NodeType.OBJECT_ENTRY_HEADER);
    return wrapExpr(requireExprChild(header));
  }

  private static Object objectEntryValue(VmTyped entryVm) {
    var expr = findExprChildVm(entryVm);
    return expr == null ? VmNull.withoutDefault() : wrapExpr(expr);
  }

  private static VmListing objectEntryObjectBodies(VmTyped entryVm) {
    return objectBodiesOf(entryVm);
  }

  private static String objectSpreadKeyword(VmTyped spreadVm) {
    return firstTerminalText(spreadVm, "...");
  }

  private static VmTyped whenCondition(VmTyped whenVm) {
    var header = requireChild(whenVm, NodeType.WHEN_GENERATOR_HEADER);
    return wrapExpr(requireExprChild(header));
  }

  private static VmTyped whenThenBody(VmTyped whenVm) {
    return objectBodyNodeFactory.create(findChildrenVm(whenVm, NodeType.OBJECT_BODY).get(0));
  }

  private static Object whenElseBody(VmTyped whenVm) {
    var bodies = findChildrenVm(whenVm, NodeType.OBJECT_BODY);
    return bodies.size() < 2
        ? VmNull.withoutDefault()
        : objectBodyNodeFactory.create(bodies.get(1));
  }

  private static List<VmTyped> forGeneratorParams(VmTyped forVm) {
    var header = findChildVm(forVm, NodeType.FOR_GENERATOR_HEADER);
    var def = header == null ? null : findChildVm(header, NodeType.FOR_GENERATOR_HEADER_DEFINITION);
    var defHeader =
        def == null ? null : findChildVm(def, NodeType.FOR_GENERATOR_HEADER_DEFINITION_HEADER);
    return defHeader == null ? new ArrayList<>() : findChildrenVm(defHeader, NodeType.PARAMETER);
  }

  private static Object forKeyParameter(VmTyped forVm) {
    var params = forGeneratorParams(forVm);
    return params.size() < 2 ? VmNull.withoutDefault() : parameterNodeFactory.create(params.get(0));
  }

  private static VmTyped forValueParameter(VmTyped forVm) {
    var params = forGeneratorParams(forVm);
    return parameterNodeFactory.create(params.get(params.size() - 1));
  }

  private static VmTyped forIterable(VmTyped forVm) {
    var header = requireChild(forVm, NodeType.FOR_GENERATOR_HEADER);
    var def = requireChild(header, NodeType.FOR_GENERATOR_HEADER_DEFINITION);
    return wrapExpr(requireExprChild(def));
  }

  private static VmTyped forBody(VmTyped forVm) {
    return objectBodyNodeFactory.create(requireChild(forVm, NodeType.OBJECT_BODY));
  }

  private static VmListing qualifiedIdentifierIdentifiers(VmTyped qualifiedVm) {
    return wrapAll(findChildrenVm(qualifiedVm, NodeType.IDENTIFIER), identifierNodeFactory);
  }

  private static String qualifiedIdentifierValue(VmTyped qualifiedVm) {
    var idVms = findChildrenVm(qualifiedVm, NodeType.IDENTIFIER);
    var builder = new StringBuilder();
    for (var i = 0; i < idVms.size(); i++) {
      if (i > 0) {
        builder.append('.');
      }
      var text = nodeText((GenericNodeData) idVms.get(i).getExtraStorage());
      builder.append(text == null ? "" : text);
    }
    return builder.toString();
  }

  // Unlike other modifiers, variance modifiers are plain terminals rather than `modifier` nodes.
  private static VmListing typeParameterModifiers(VmTyped typeParameterVm) {
    var data = (GenericNodeData) typeParameterVm.getExtraStorage();
    var result = new ArrayList<>();
    for (var child : data.node.children) {
      if (child.type == NodeType.TERMINAL) {
        var text = child.text(data.source);
        if ("in".equals(text) || "out".equals(text)) {
          result.add(text);
        }
      }
    }
    return listingOf(result.toArray());
  }

  private static boolean parameterIsBlankIdentifier(VmTyped parameterVm) {
    return findChildVm(parameterVm, NodeType.IDENTIFIER) == null;
  }

  private static Object parameterIdentifier(VmTyped parameterVm) {
    var id = findChildVm(parameterVm, NodeType.IDENTIFIER);
    return id == null ? VmNull.withoutDefault() : identifierNodeFactory.create(id);
  }

  private static Object parameterTypeAnnotation(VmTyped parameterVm) {
    return typeAnnotationOf(parameterVm);
  }

  private static VmListing buildStringParts(VmTyped stringVm) {
    var data = (GenericNodeData) stringVm.getExtraStorage();
    var children = data.node.children;
    var childrenVm = data.childrenVm;
    var parts = new ArrayList<>();
    // skip the opening and closing quote terminals
    var end = children.size() - 1;
    var i = 1;
    while (i < end) {
      var child = children.get(i);
      switch (child.type) {
        case STRING_CHARS -> {
          parts.add(stringCharsNodeFactory.create((VmTyped) childrenVm.get(i)));
          i++;
        }
        case STRING_ESCAPE -> {
          parts.add(stringEscapeNodeFactory.create((VmTyped) childrenVm.get(i)));
          i++;
        }
        case STRING_NEWLINE -> {
          parts.add(stringNewlineNodeFactory.create((VmTyped) childrenVm.get(i)));
          i++;
        }
        case TERMINAL -> {
          if (isInterpolationStart(child, data.source)) {
            var exprIdx = nextNonAffix(children, i + 1, end);
            if (exprIdx >= end) {
              i = end;
            } else {
              parts.add(stringInterpolationNodeFactory.create((VmTyped) childrenVm.get(exprIdx)));
              i = nextNonAffix(children, exprIdx + 1, end) + 1;
            }
          } else {
            i++;
          }
        }
        default -> i++; // affixes and stray terminals
      }
    }
    return listingOf(parts.toArray());
  }

  private static boolean isInterpolationStart(Node terminal, char[] source) {
    var text = terminal.text(source);
    return text.endsWith("(") && (text.startsWith("\\") || text.startsWith("#"));
  }

  // The next index in `[start, end)` whose node is not an affix (comment/semicolon), else `end`.
  private static int nextNonAffix(List<Node> children, int start, int end) {
    var i = start;
    while (i < end) {
      var type = children.get(i).type;
      if (type == NodeType.LINE_COMMENT
          || type == NodeType.BLOCK_COMMENT
          || type == NodeType.SEMICOLON) {
        i++;
      } else {
        return i;
      }
    }
    return i;
  }

  private static VmListing moduleDeclModifiers(VmTyped declVm) {
    var moduleDefinition = findChildVm(declVm, NodeType.MODULE_DEFINITION);
    return moduleDefinition == null ? VmListing.empty() : modifiersOf(moduleDefinition);
  }

  private static Object moduleDeclName(VmTyped declVm) {
    var moduleDefinition = findChildVm(declVm, NodeType.MODULE_DEFINITION);
    if (moduleDefinition == null) {
      return VmNull.withoutDefault();
    }
    var name = findChildVm(moduleDefinition, NodeType.QUALIFIED_IDENTIFIER);
    return name == null ? VmNull.withoutDefault() : qualifiedIdentifierNodeFactory.create(name);
  }

  private static Object moduleDeclExtendsOrAmendsClause(VmTyped declVm) {
    var clause = findChildVm(declVm, NodeType.AMENDS_CLAUSE);
    if (clause == null) {
      clause = findChildVm(declVm, NodeType.EXTENDS_CLAUSE);
    }
    return clause == null
        ? VmNull.withoutDefault()
        : extendsOrAmendsClauseNodeFactory.create(clause);
  }

  private static String extendsOrAmendsClauseKeyword(VmTyped clauseVm) {
    var data = (GenericNodeData) clauseVm.getExtraStorage();
    return data.node.type == NodeType.AMENDS_CLAUSE ? "amends" : "extends";
  }

  // The typed classes, typealiases, properties, and methods among `ownerVm`'s children,
  // in source order.
  private static VmListing memberChildren(VmTyped ownerVm) {
    var data = (GenericNodeData) ownerVm.getExtraStorage();
    var children = data.node.children;
    var result = new ArrayList<>();
    for (var i = 0; i < children.size(); i++) {
      var factory =
          switch (children.get(i).type) {
            case CLASS -> classNodeFactory;
            case TYPEALIAS -> typeAliasNodeFactory;
            case CLASS_PROPERTY -> classPropertyNodeFactory;
            case CLASS_METHOD -> classMethodNodeFactory;
            default -> null;
          };
      if (factory != null) {
        result.add(factory.create((VmTyped) data.childrenVm.get(i)));
      }
    }
    return listingOf(result.toArray());
  }

  private static VmListing classModifiers(VmTyped classVm) {
    var header = findChildVm(classVm, NodeType.CLASS_HEADER);
    return header == null ? VmListing.empty() : modifiersOf(header);
  }

  private static VmTyped classIdentifier(VmTyped classVm) {
    return identifierNodeOf(findChildVm(classVm, NodeType.CLASS_HEADER));
  }

  private static VmListing classTypeParameters(VmTyped classVm) {
    return typeParametersOf(findChildVm(classVm, NodeType.CLASS_HEADER));
  }

  private static Object classSuperType(VmTyped classVm) {
    var header = findChildVm(classVm, NodeType.CLASS_HEADER);
    if (header == null) {
      return VmNull.withoutDefault();
    }
    var ext = findChildVm(header, NodeType.CLASS_HEADER_EXTENDS);
    if (ext == null) {
      return VmNull.withoutDefault();
    }
    var type = findTypeChildVm(ext);
    return type == null ? VmNull.withoutDefault() : wrapType(type);
  }

  private static Object classBody(VmTyped classVm) {
    var body = findChildVm(classVm, NodeType.CLASS_BODY);
    return body == null ? VmNull.withoutDefault() : classBodyNodeFactory.create(body);
  }

  private static VmListing typeAliasModifiers(VmTyped typeAliasVm) {
    var header = findChildVm(typeAliasVm, NodeType.TYPEALIAS_HEADER);
    return header == null ? VmListing.empty() : modifiersOf(header);
  }

  private static VmTyped typeAliasIdentifier(VmTyped typeAliasVm) {
    return identifierNodeOf(findChildVm(typeAliasVm, NodeType.TYPEALIAS_HEADER));
  }

  private static VmListing typeAliasTypeParameters(VmTyped typeAliasVm) {
    return typeParametersOf(findChildVm(typeAliasVm, NodeType.TYPEALIAS_HEADER));
  }

  private static VmTyped typeAliasType(VmTyped typeAliasVm) {
    var body = findChildVm(typeAliasVm, NodeType.TYPEALIAS_BODY);
    var type = body == null ? null : findTypeChildVm(body);
    if (type == null) {
      throw new VmExceptionBuilder().bug("A parsed `typealias` always has a body type.").build();
    }
    return wrapType(type);
  }

  private static @Nullable VmTyped classPropertyHeaderBegin(VmTyped propertyVm) {
    var propHeader = findChildVm(propertyVm, NodeType.CLASS_PROPERTY_HEADER);
    return propHeader == null
        ? null
        : findChildVm(propHeader, NodeType.CLASS_PROPERTY_HEADER_BEGIN);
  }

  private static VmListing classPropertyModifiers(VmTyped propertyVm) {
    var headerBegin = classPropertyHeaderBegin(propertyVm);
    return headerBegin == null ? VmListing.empty() : modifiersOf(headerBegin);
  }

  private static VmTyped classPropertyIdentifier(VmTyped propertyVm) {
    return identifierNodeOf(classPropertyHeaderBegin(propertyVm));
  }

  private static Object classPropertyTypeAnnotation(VmTyped propertyVm) {
    return typeAnnotationOf(findChildVm(propertyVm, NodeType.CLASS_PROPERTY_HEADER));
  }

  private static Object classPropertyValue(VmTyped propertyVm) {
    return exprInChild(propertyVm, NodeType.CLASS_PROPERTY_BODY);
  }

  private static VmListing classPropertyObjectBodies(VmTyped propertyVm) {
    return objectBodiesOf(propertyVm);
  }

  private static VmListing classMethodModifiers(VmTyped methodVm) {
    var header = findChildVm(methodVm, NodeType.CLASS_METHOD_HEADER);
    return header == null ? VmListing.empty() : modifiersOf(header);
  }

  private static VmTyped classMethodIdentifier(VmTyped methodVm) {
    return identifierNodeOf(findChildVm(methodVm, NodeType.CLASS_METHOD_HEADER));
  }

  private static VmListing classMethodTypeParameters(VmTyped methodVm) {
    return typeParametersOf(methodVm);
  }

  private static VmListing classMethodParameters(VmTyped methodVm) {
    return parametersOf(methodVm);
  }

  private static Object classMethodReturnType(VmTyped methodVm) {
    return typeAnnotationOf(methodVm);
  }

  private static Object classMethodBody(VmTyped methodVm) {
    return exprInChild(methodVm, NodeType.CLASS_METHOD_BODY);
  }

  private static VmListing classBodyMembers(VmTyped classBodyVm) {
    var elements = findChildVm(classBodyVm, NodeType.CLASS_BODY_ELEMENTS);
    if (elements == null) {
      return VmListing.empty();
    }
    return memberChildren(elements);
  }

  private static VmListing moduleImports(VmTyped moduleVm) {
    var importListVm = findChildVm(moduleVm, NodeType.IMPORT_LIST);
    if (importListVm == null) {
      return VmListing.empty();
    }
    return wrapAll(findChildrenVm(importListVm, NodeType.IMPORT), importNodeFactory);
  }

  private static String importKeyword(VmTyped importVm) {
    return firstTerminalText(importVm, "import");
  }

  private static String importUri(VmTyped importVm) {
    var data = (GenericNodeData) importVm.getExtraStorage();
    return extractStringChars(data.node, data.source);
  }

  private static Object importAlias(VmTyped importVm) {
    var aliasVm = findChildVm(importVm, NodeType.IMPORT_ALIAS);
    if (aliasVm == null) {
      return VmNull.withoutDefault();
    }
    // an `import_alias` node always contains an `identifier`
    return identifierNodeFactory.create(findChildVm(aliasVm, NodeType.IDENTIFIER));
  }

  private static String identifierText(VmTyped identifierVm) {
    var text = nodeText((GenericNodeData) identifierVm.getExtraStorage());
    return text == null ? "" : text;
  }

  private static @Nullable String nodeText(GenericNodeData data) {
    return data.node.children.isEmpty() || data.node.type == NodeType.STRING_CHARS
        ? data.node.text(data.source)
        : null;
  }

  // The text of the node's first terminal child, or `fallback` if it has none
  private static String firstTerminalText(VmTyped nodeVm, String fallback) {
    var data = (GenericNodeData) nodeVm.getExtraStorage();
    for (var child : data.node.children) {
      if (child.type == NodeType.TERMINAL) {
        return child.text(data.source);
      }
    }
    return fallback;
  }

  // Extract the string constant from a node's `string_chars` child (dropping the enclosing quotes)
  private static String extractStringChars(Node node, char[] source) {
    var stringChars = node.findChildByType(NodeType.STRING_CHARS);
    if (stringChars == null) {
      return "";
    }
    var terminals = new ArrayList<Node>();
    for (var child : stringChars.children) {
      if (child.type == NodeType.TERMINAL) {
        terminals.add(child);
      }
    }
    var builder = new StringBuilder();
    for (var i = 1; i < terminals.size() - 1; i++) {
      var text = terminals.get(i).text(source);
      builder.append(text);
    }
    return builder.toString();
  }

  // A `Listing` holding `elements`, in order.
  private static VmListing listingOf(Object[] elements) {
    return VmList.create(elements).toListing();
  }

  private static VmListing modifiersOf(VmTyped ownerVm) {
    var modifierList = findChildVm(ownerVm, NodeType.MODIFIER_LIST);
    if (modifierList == null) {
      return VmListing.empty();
    }
    var modifierVms = findChildrenVm(modifierList, NodeType.MODIFIER);
    var result = new Object[modifierVms.size()];
    for (var i = 0; i < modifierVms.size(); i++) {
      var data = (GenericNodeData) modifierVms.get(i).getExtraStorage();
      result[i] = data.node.text(data.source);
    }
    return listingOf(result);
  }

  private static String stringCharsOf(VmTyped clauseVm) {
    var data = (GenericNodeData) clauseVm.getExtraStorage();
    return extractStringChars(data.node, data.source);
  }

  // The first child of `genericVm` with the given type, as a generic-node `VmTyped`, or null.
  private static @Nullable VmTyped findChildVm(VmTyped genericVm, NodeType type) {
    var data = (GenericNodeData) genericVm.getExtraStorage();
    var children = data.node.children;
    for (var i = 0; i < children.size(); i++) {
      if (children.get(i).type == type) {
        return (VmTyped) data.childrenVm.get(i);
      }
    }
    return null;
  }

  // The first type-node child of `genericVm`, as a generic-node `VmTyped`, or null.
  private static @Nullable VmTyped findTypeChildVm(VmTyped genericVm) {
    var data = (GenericNodeData) genericVm.getExtraStorage();
    var children = data.node.children;
    for (var i = 0; i < children.size(); i++) {
      if (children.get(i).type.isType()) {
        return (VmTyped) data.childrenVm.get(i);
      }
    }
    return null;
  }

  // The first expression-node child of `genericVm`, as a generic-node `VmTyped`, or null.
  private static @Nullable VmTyped findExprChildVm(VmTyped genericVm) {
    var data = (GenericNodeData) genericVm.getExtraStorage();
    var children = data.node.children;
    for (var i = 0; i < children.size(); i++) {
      if (children.get(i).type.isExpression()) {
        return (VmTyped) data.childrenVm.get(i);
      }
    }
    return null;
  }

  // All type-node children of `genericVm`, as generic-node `VmTyped`s.
  private static List<VmTyped> findTypeChildrenVm(VmTyped genericVm) {
    var data = (GenericNodeData) genericVm.getExtraStorage();
    var children = data.node.children;
    var result = new ArrayList<VmTyped>();
    for (var i = 0; i < children.size(); i++) {
      if (children.get(i).type.isType()) {
        result.add((VmTyped) data.childrenVm.get(i));
      }
    }
    return result;
  }

  // All expression-node children of `genericVm`, as generic-node `VmTyped`s.
  private static List<VmTyped> findExprChildrenVm(VmTyped genericVm) {
    var data = (GenericNodeData) genericVm.getExtraStorage();
    var children = data.node.children;
    var result = new ArrayList<VmTyped>();
    for (var i = 0; i < children.size(); i++) {
      if (children.get(i).type.isExpression()) {
        result.add((VmTyped) data.childrenVm.get(i));
      }
    }
    return result;
  }

  // Wrap each generic type node into its `TypeNode` subclass, as a `VmListing`.
  private static VmListing wrapTypes(List<VmTyped> typeVms) {
    var result = new Object[typeVms.size()];
    for (var i = 0; i < typeVms.size(); i++) {
      result[i] = wrapType(typeVms.get(i));
    }
    return listingOf(result);
  }

  // Wrap each generic expression node into its `ExprNode` subclass, as a `VmListing`.
  private static VmListing wrapExprs(List<VmTyped> exprVms) {
    var result = new Object[exprVms.size()];
    for (var i = 0; i < exprVms.size(); i++) {
      result[i] = wrapExpr(exprVms.get(i));
    }
    return listingOf(result);
  }

  private static Object typeAnnotationOf(@Nullable VmTyped ownerVm) {
    if (ownerVm == null) {
      return VmNull.withoutDefault();
    }
    var annotation = findChildVm(ownerVm, NodeType.TYPE_ANNOTATION);
    if (annotation == null) {
      return VmNull.withoutDefault();
    }
    var type = findTypeChildVm(annotation);
    if (type == null) {
      throw new VmExceptionBuilder().bug("A `type_annotation` always contains a type.").build();
    }
    return wrapType(type);
  }

  // The expression inside a `containerType` child of `ownerVm`, as an ExprNode, or null when
  // absent.
  private static Object exprInChild(VmTyped ownerVm, NodeType containerType) {
    var container = findChildVm(ownerVm, containerType);
    if (container == null) {
      return VmNull.withoutDefault();
    }
    var expr = findExprChildVm(container);
    return expr == null ? VmNull.withoutDefault() : wrapExpr(expr);
  }

  private static VmListing objectBodiesOf(VmTyped ownerVm) {
    return wrapAll(findChildrenVm(ownerVm, NodeType.OBJECT_BODY), objectBodyNodeFactory);
  }

  private static VmListing parametersOf(VmTyped ownerVm) {
    var list = findChildVm(ownerVm, NodeType.PARAMETER_LIST);
    if (list == null) {
      return VmListing.empty();
    }
    var elems = findChildVm(list, NodeType.PARAMETER_LIST_ELEMENTS);
    if (elems == null) {
      return VmListing.empty();
    }
    return wrapAll(findChildrenVm(elems, NodeType.PARAMETER), parameterNodeFactory);
  }

  // Wrap each generic-node `VmTyped` into a typed node via `factory`, as a `VmListing`.
  private static VmListing wrapAll(List<VmTyped> genericVms, VmObjectFactory<VmTyped> factory) {
    var result = new Object[genericVms.size()];
    for (var i = 0; i < genericVms.size(); i++) {
      result[i] = factory.create(genericVms.get(i));
    }
    return listingOf(result);
  }

  private static VmTyped identifierNodeOf(@Nullable VmTyped ownerVm) {
    var id = ownerVm == null ? null : findChildVm(ownerVm, NodeType.IDENTIFIER);
    return identifierNodeFactory.create(id);
  }

  private static VmListing typeParametersOf(@Nullable VmTyped ownerVm) {
    if (ownerVm == null) {
      return VmListing.empty();
    }
    var list = findChildVm(ownerVm, NodeType.TYPE_PARAMETER_LIST);
    if (list == null) {
      return VmListing.empty();
    }
    var elems = findChildVm(list, NodeType.TYPE_PARAMETER_LIST_ELEMENTS);
    if (elems == null) {
      return VmListing.empty();
    }
    return wrapAll(findChildrenVm(elems, NodeType.TYPE_PARAMETER), typeParameterNodeFactory);
  }

  // Wrap a generic type node into its specific `TypeNode` subclass
  private static VmTyped wrapType(VmTyped typeVm) {
    var data = (GenericNodeData) typeVm.getExtraStorage();
    return switch (data.node.type) {
      case UNKNOWN_TYPE -> unknownTypeNodeFactory.create(typeVm);
      case NOTHING_TYPE -> nothingTypeNodeFactory.create(typeVm);
      case MODULE_TYPE -> moduleTypeNodeFactory.create(typeVm);
      case DECLARED_TYPE -> declaredTypeNodeFactory.create(typeVm);
      case NULLABLE_TYPE -> nullableTypeNodeFactory.create(typeVm);
      case UNION_TYPE -> unionTypeNodeFactory.create(typeVm);
      case FUNCTION_TYPE -> functionTypeNodeFactory.create(typeVm);
      case CONSTRAINED_TYPE -> constrainedTypeNodeFactory.create(typeVm);
      case PARENTHESIZED_TYPE -> parenthesizedTypeNodeFactory.create(typeVm);
      case STRING_CONSTANT_TYPE -> stringLiteralTypeNodeFactory.create(typeVm);
      default ->
          throw new VmExceptionBuilder().bug("Unexpected type node: " + data.node.type).build();
    };
  }

  // Wrap a generic expression node into its specific `ExprNode` subclass
  private static VmTyped wrapExpr(VmTyped exprVm) {
    var data = (GenericNodeData) exprVm.getExtraStorage();
    return switch (data.node.type) {
      case THIS_EXPR -> thisExprNodeFactory.create(exprVm);
      case OUTER_EXPR -> outerExprNodeFactory.create(exprVm);
      case MODULE_EXPR -> moduleExprNodeFactory.create(exprVm);
      case NULL_EXPR -> nullLiteralExprNodeFactory.create(exprVm);
      case BOOL_LITERAL_EXPR -> booleanLiteralExprNodeFactory.create(exprVm);
      case INT_LITERAL_EXPR -> intLiteralExprNodeFactory.create(exprVm);
      case FLOAT_LITERAL_EXPR -> floatLiteralExprNodeFactory.create(exprVm);
      case SINGLE_LINE_STRING_LITERAL_EXPR -> singleLineStringLiteralExprNodeFactory.create(exprVm);
      case MULTI_LINE_STRING_LITERAL_EXPR -> multiLineStringLiteralExprNodeFactory.create(exprVm);
      case UNQUALIFIED_ACCESS_EXPR -> unqualifiedAccessExprNodeFactory.create(exprVm);
      case QUALIFIED_ACCESS_EXPR -> qualifiedAccessExprNodeFactory.create(exprVm);
      case SUBSCRIPT_EXPR -> subscriptExprNodeFactory.create(exprVm);
      case SUPER_ACCESS_EXPR -> superAccessExprNodeFactory.create(exprVm);
      case SUPER_SUBSCRIPT_EXPR -> superSubscriptExprNodeFactory.create(exprVm);
      case IF_EXPR -> ifExprNodeFactory.create(exprVm);
      case LET_EXPR -> letExprNodeFactory.create(exprVm);
      case THROW_EXPR -> throwExprNodeFactory.create(exprVm);
      case TRACE_EXPR -> traceExprNodeFactory.create(exprVm);
      case IMPORT_EXPR -> importExprNodeFactory.create(exprVm);
      case READ_EXPR -> readExprNodeFactory.create(exprVm);
      case NEW_EXPR -> newExprNodeFactory.create(exprVm);
      case AMENDS_EXPR -> amendsExprNodeFactory.create(exprVm);
      case BINARY_OP_EXPR -> binaryOpFactory(exprVm).create(exprVm);
      case UNARY_MINUS_EXPR -> unaryMinusExprNodeFactory.create(exprVm);
      case LOGICAL_NOT_EXPR -> logicalNotExprNodeFactory.create(exprVm);
      case NON_NULL_EXPR -> nonNullExprNodeFactory.create(exprVm);
      case FUNCTION_LITERAL_EXPR -> functionLiteralExprNodeFactory.create(exprVm);
      case PARENTHESIZED_EXPR -> parenthesizedExprNodeFactory.create(exprVm);
      default ->
          throw new VmExceptionBuilder()
              .bug("Unexpected expression node: " + data.node.type)
              .build();
    };
  }

  // All children of `genericVm` with the given type, as generic-node `VmTyped`s.
  private static List<VmTyped> findChildrenVm(VmTyped genericVm, NodeType type) {
    var data = (GenericNodeData) genericVm.getExtraStorage();
    var children = data.node.children;
    var result = new ArrayList<VmTyped>();
    for (var i = 0; i < children.size(); i++) {
      if (children.get(i).type == type) {
        result.add((VmTyped) data.childrenVm.get(i));
      }
    }
    return result;
  }

  public abstract static class parseModule extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object evalString(@SuppressWarnings("unused") VmTyped self, String source) {
      return doParse(source, null);
    }

    @Specialization
    @TruffleBoundary
    protected Object evalResource(@SuppressWarnings("unused") VmTyped self, VmTyped source) {
      // `source` is a `pkl.base#Resource`
      var text = (String) VmUtils.readMember(source, Identifier.TEXT);
      return doParse(text, sourceUri(source));
    }
  }

  public abstract static class parseModuleOrNull extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object evalString(@SuppressWarnings("unused") VmTyped self, String source) {
      return doParseOrNull(source, null);
    }

    @Specialization
    @TruffleBoundary
    protected Object evalResource(@SuppressWarnings("unused") VmTyped self, VmTyped source) {
      // `source` is a `pkl.base#Resource`
      var text = (String) VmUtils.readMember(source, Identifier.TEXT);
      return doParseOrNull(text, sourceUri(source));
    }
  }

  private static String sourceUri(VmTyped resource) {
    return VmUtils.readMember(resource, Identifier.URI).toString();
  }

  private static Object doParse(String src, @Nullable String sourceUri) {
    var sourceChars = src.toCharArray();
    try {
      var parser = new GenericParser();
      var root = parser.parseModule(src);
      var genericNode = convertNode(root, sourceChars, sourceUri);
      return moduleNodeFactory.create(genericNode);
    } catch (GenericParserError e) {
      throw new VmExceptionBuilder().evalError("parserError").withHint(e.toString()).build();
    }
  }

  private static Object doParseOrNull(String src, @Nullable String sourceUri) {
    var sourceChars = src.toCharArray();
    try {
      var parser = new GenericParser();
      var root = parser.parseModule(src);
      var genericNode = convertNode(root, sourceChars, sourceUri);
      return moduleNodeFactory.create(genericNode);
    } catch (GenericParserError e) {
      return VmNull.withoutDefault();
    }
  }

  private static VmTyped convertNode(
      Node genericNode, char[] sourceChars, @Nullable String sourceUri) {
    // convert children recursively
    var childrenList = new ArrayList<VmTyped>(genericNode.children.size());
    for (var child : genericNode.children) {
      childrenList.add(convertNode(child, sourceChars, sourceUri));
    }

    // materialize text now so that nodes reused verbatim by `walk`/`format` are
    // self-contained
    if (genericNode.children.isEmpty() || genericNode.type == NodeType.STRING_CHARS) {
      genericNode.text(sourceChars);
    }

    var childrenVm = VmList.create(childrenList.toArray());
    var spanVm = SyntaxNodes.spanFactory.create(new SpanData(genericNode.span, sourceUri));
    var data = new GenericNodeData(genericNode, sourceChars, childrenVm, spanVm);

    var result = genericNodeFactory.create(data);

    // set parent back-reference on each child
    for (var childVm : childrenList) {
      var childData = (GenericNodeData) childVm.getExtraStorage();
      childData.parentVm = result;
    }

    return result;
  }
}
