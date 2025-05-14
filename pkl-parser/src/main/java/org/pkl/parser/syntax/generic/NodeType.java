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
  LINE_COMMENT,
  BLOCK_COMMENT,
  SHEBANG,
  SEMICOLON,

  MODULE,
  DOC_COMMENT,
  DOC_COMMENT_LINE,
  MODIFIER,
  AMENDS_CLAUSE,
  EXTENDS_CLAUSE,
  MODULE_DECLARATION,
  MODULE_DEFINITION,
  ANNOTATION,
  IDENTIFIER,
  QUALIFIED_IDENTIFIER,
  IMPORT,
  IMPORT_LIST,
  TYPEALIAS,
  CLASS,
  CLASS_BODY,
  CLASS_METHOD,
  CLASS_PROPERTY,
  OBJECT_BODY,
  PARAMETER,
  TYPE_ANNOTATION,
  PARAMETER_LIST,
  TYPE_PARAMETER_LIST,
  ARGUMENT_LIST,
  TYPE_ARGUMENT_LIST,
  TYPE_PARAMETER,
  STRING_CONSTANT,
  OPERATOR,
  STRING_ESCAPE,

  // members
  OBJECT_ELEMENT,
  OBJECT_PROPERTY,
  OBJECT_METHOD,
  MEMBER_PREDICATE,
  OBJECT_ENTRY,
  OBJECT_SPREAD,
  WHEN_GENERATOR,
  FOR_GENERATOR,

  // expressions
  THIS_EXPR,
  OUTER_EXPR,
  MODULE_EXPR,
  NULL_EXPR,
  THROW_EXPR,
  TRACE_EXPR,
  IMPORT_EXPR,
  READ_EXPR,
  NEW_EXPR,
  UNARY_MINUS_EXPR,
  LOGICAL_NOT_EXPR,
  FUNCTION_LITERAL_EXPR,
  PARENTHESIZED_EXPR,
  SUPER_SUBSCRIPT_EXPR,
  SUPER_ACCESS_EXPR,
  SUBSCRIPT_EXPR,
  IF_EXPR,
  LET_EXPR,
  BOOL_LITERAL_EXPR,
  INT_LITERAL_EXPR,
  FLOAT_LITERAL_EXPR,
  SINGLE_LINE_STRING_LITERAL_EXPR,
  MULTI_LINE_STRING_LITERAL_EXPR,
  UNQUALIFIED_ACCESS_EXPR,
  QUALIFIED_ACCESS_EXPR,
  NON_NULL_EXPR,
  AMENDS_EXPR,
  BINARY_OP_EXPR,

  // types
  UNKNOWN_TYPE,
  NOTHING_TYPE,
  MODULE_TYPE,
  UNION_TYPE,
  FUNCTION_TYPE,
  PARENTHESIZED_TYPE,
  DECLARED_TYPE,
  NULLABLE_TYPE,
  STRING_CONSTANT_TYPE,
  CONSTRAINED_TYPE;

  public boolean isAffix() {
    return switch (this) {
      case LINE_COMMENT, BLOCK_COMMENT, SEMICOLON, SHEBANG -> true;
      default -> false;
    };
  }
}
