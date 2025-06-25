/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.parser.syntax.generic;

public enum NodeType {
  TERMINAL,
  // affixes
  LINE_COMMENT(NodeKind.AFFIX),
  BLOCK_COMMENT(NodeKind.AFFIX),
  SHEBANG(NodeKind.AFFIX),
  SEMICOLON(NodeKind.AFFIX),

  MODULE,
  DOC_COMMENT,
  DOC_COMMENT_LINE,
  MODIFIER,
  MODIFIER_LIST,
  AMENDS_CLAUSE,
  EXTENDS_CLAUSE,
  MODULE_DECLARATION,
  MODULE_DEFINITION,
  ANNOTATION,
  IDENTIFIER,
  QUALIFIED_IDENTIFIER,
  IMPORT,
  IMPORT_ALIAS,
  IMPORT_LIST,
  TYPEALIAS,
  TYPEALIAS_HEADER,
  TYPEALIAS_BODY,
  CLASS,
  CLASS_HEADER,
  CLASS_BODY,
  CLASS_BODY_ELEMENTS,
  CLASS_METHOD,
  CLASS_METHOD_HEADER,
  CLASS_METHOD_BODY,
  CLASS_PROPERTY,
  CLASS_PROPERTY_HEADER,
  CLASS_PROPERTY_HEADER_BEGIN,
  OBJECT_BODY,
  OBJECT_MEMBER_LIST,
  PARAMETER,
  TYPE_ANNOTATION,
  PARAMETER_LIST,
  PARAMETER_LIST_ELEMENTS,
  TYPE_PARAMETER_LIST,
  TYPE_PARAMETER_LIST_ELEMENTS,
  ARGUMENT_LIST,
  ARGUMENT_LIST_ELEMENTS,
  TYPE_ARGUMENT_LIST,
  TYPE_ARGUMENT_LIST_ELEMENTS,
  OBJECT_PARAMETER_LIST,
  TYPE_PARAMETER,
  STRING_CONSTANT,
  OPERATOR,
  STRING_NEWLINE,
  STRING_ESCAPE,

  // members
  OBJECT_ELEMENT,
  OBJECT_PROPERTY,
  OBJECT_PROPERTY_HEADER,
  OBJECT_PROPERTY_HEADER_BEGIN,
  OBJECT_METHOD,
  MEMBER_PREDICATE,
  OBJECT_ENTRY,
  OBJECT_ENTRY_HEADER,
  OBJECT_SPREAD,
  WHEN_GENERATOR,
  WHEN_GENERATOR_HEADER,
  FOR_GENERATOR,
  FOR_GENERATOR_HEADER,
  FOR_GENERATOR_HEADER_DEFINITION,
  FOR_GENERATOR_HEADER_DEFINITION_HEADER,

  // expressions
  THIS_EXPR(NodeKind.EXPR),
  OUTER_EXPR(NodeKind.EXPR),
  MODULE_EXPR(NodeKind.EXPR),
  NULL_EXPR(NodeKind.EXPR),
  THROW_EXPR(NodeKind.EXPR),
  TRACE_EXPR(NodeKind.EXPR),
  IMPORT_EXPR(NodeKind.EXPR),
  READ_EXPR(NodeKind.EXPR),
  NEW_EXPR(NodeKind.EXPR),
  NEW_HEADER,
  UNARY_MINUS_EXPR(NodeKind.EXPR),
  LOGICAL_NOT_EXPR(NodeKind.EXPR),
  FUNCTION_LITERAL_EXPR(NodeKind.EXPR),
  FUNCTION_LITERAL_BODY,
  PARENTHESIZED_EXPR(NodeKind.EXPR),
  SUPER_SUBSCRIPT_EXPR(NodeKind.EXPR),
  SUPER_ACCESS_EXPR(NodeKind.EXPR),
  SUBSCRIPT_EXPR(NodeKind.EXPR),
  IF_EXPR(NodeKind.EXPR),
  IF_HEADER,
  IF_CONDITION,
  IF_THEN_EXPR,
  IF_ELSE_EXPR,
  LET_EXPR(NodeKind.EXPR),
  LET_PARAMETER_DEFINITION,
  LET_PARAMETER,
  BOOL_LITERAL_EXPR(NodeKind.EXPR),
  INT_LITERAL_EXPR(NodeKind.EXPR),
  FLOAT_LITERAL_EXPR(NodeKind.EXPR),
  SINGLE_LINE_STRING_LITERAL_EXPR(NodeKind.EXPR),
  MULTI_LINE_STRING_LITERAL_EXPR(NodeKind.EXPR),
  UNQUALIFIED_ACCESS_EXPR(NodeKind.EXPR),
  NON_NULL_EXPR(NodeKind.EXPR),
  AMENDS_EXPR(NodeKind.EXPR),
  BINARY_OP_EXPR(NodeKind.EXPR),

  // types
  UNKNOWN_TYPE(NodeKind.TYPE),
  NOTHING_TYPE(NodeKind.TYPE),
  MODULE_TYPE(NodeKind.TYPE),
  UNION_TYPE(NodeKind.TYPE),
  FUNCTION_TYPE(NodeKind.TYPE),
  PARENTHESIZED_TYPE(NodeKind.TYPE),
  DECLARED_TYPE(NodeKind.TYPE),
  NULLABLE_TYPE(NodeKind.TYPE),
  STRING_CONSTANT_TYPE(NodeKind.TYPE),
  CONSTRAINED_TYPE(NodeKind.TYPE),
  CONSTRAINED_TYPE_CONSTRAINT,
  CONSTRAINED_TYPE_ELEMENTS;

  private final NodeKind kind;

  NodeType() {
    this.kind = NodeKind.NONE;
  }

  NodeType(NodeKind kind) {
    this.kind = kind;
  }

  public boolean isAffix() {
    return kind == NodeKind.AFFIX;
  }

  public boolean isExpression() {
    return kind == NodeKind.EXPR;
  }

  public boolean isType() {
    return kind == NodeKind.TYPE;
  }

  private enum NodeKind {
    TYPE,
    EXPR,
    AFFIX,
    NONE;
  }
}
