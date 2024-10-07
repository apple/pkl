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
parser grammar PklParser;

@header {
package org.pkl.core.parser.antlr;
}

@members {
/**
 * Returns true if and only if the next token to be consumed is not preceded by a newline or semicolon.
 */
boolean noNewlineOrSemicolon() {
  for (int i = _input.index() - 1; i >= 0; i--) {
    Token token = _input.get(i);
    int channel = token.getChannel();
    if (channel == PklLexer.DEFAULT_TOKEN_CHANNEL) return true;
    if (channel == PklLexer.NewlineSemicolonChannel) return false;
  }
  return true;
}
}

options {
  tokenVocab = PklLexer;
}

replInput
  : ((moduleDecl
  | importClause
  | clazz
  | typeAlias
  | classProperty
  | classMethod
  | expr))* EOF
  ;

exprInput
  : expr EOF
  ;

module
  : moduleDecl? (is+=importClause)* ((cs+=clazz | ts+=typeAlias | ps+=classProperty | ms+=classMethod))* EOF
  ;

moduleDecl
  : t=DocComment? annotation* moduleHeader
  ;

moduleHeader
  : modifier* 'module' qualifiedIdentifier moduleExtendsOrAmendsClause?
  | moduleExtendsOrAmendsClause
  ;

moduleExtendsOrAmendsClause
  : t=('extends' | 'amends') stringConstant
  ;

importClause
  : t=('import' | 'import*') stringConstant ('as' Identifier)?
  ;

clazz
  : t=DocComment? annotation* classHeader classBody?
  ;

classHeader
  : modifier* 'class' Identifier typeParameterList? ('extends' type)?
  ;

modifier
  : t=('external' | 'abstract' | 'open' | 'local' | 'hidden' | 'fixed' | 'const')
  ;

classBody
  : '{' ((ps+=classProperty | ms+=classMethod))* err='}'?
  ;

typeAlias
  : t=DocComment? annotation* typeAliasHeader '=' type
  ;

typeAliasHeader
  : modifier* 'typealias' Identifier typeParameterList?
  ;

// allows `foo: Bar { ... }` s.t. AstBuilder can provide better error message
classProperty
  : t=DocComment? annotation* modifier* Identifier (typeAnnotation | typeAnnotation? ('=' expr | objectBody+))
  ;

classMethod
  : t=DocComment? annotation* methodHeader ('=' expr)?
  ;

methodHeader
  : modifier* 'function' Identifier typeParameterList? parameterList typeAnnotation?
  ;

parameterList
  : '(' (ts+=parameter (errs+=','? ts+=parameter)*)? err=')'?
  ;

argumentList
  : {noNewlineOrSemicolon()}? '(' (es+=expr (errs+=','? es+=expr)*)? err=')'?
  ;

annotation
  : '@' type objectBody?
  ;

qualifiedIdentifier
  : ts+=Identifier ('.' ts+=Identifier)*
  ;

typeAnnotation
  : ':' type
  ;

typeParameterList
  : '<' ts+=typeParameter (errs+=','? ts+=typeParameter)* err='>'?
  ;

typeParameter
  : t=('in' | 'out')? Identifier
  ;

typeArgumentList
  : '<' ts+=type (errs+=','? ts+=type)* err='>'?
  ;

type
  : 'unknown'                                                                     # unknownType
  | 'nothing'                                                                     # nothingType
  | 'module'                                                                      # moduleType
  | stringConstant                                                                # stringLiteralType
  | qualifiedIdentifier typeArgumentList?                                         # declaredType
  | '(' type err=')'?                                                             # parenthesizedType
  | type '?'                                                                      # nullableType
  | type {noNewlineOrSemicolon()}? t='(' es+=expr (errs+=','? es+=expr)* err=')'? # constrainedType
  | '*' u=type                                                                    # defaultUnionType
  | l=type '|' r=type                                                             # unionType
  | t='(' (ps+=type (errs+=','? ps+=type)*)? err=')'? '->' r=type                 # functionType
  ;

typedIdentifier
  : Identifier typeAnnotation?
  ;

parameter
  : '_'
  | typedIdentifier
  ;

// Many languages (e.g., Python) give `**` higher precedence than unary minus.
// The reason is that in Math, `-a^2` means `-(a^2)`.
// To avoid confusion, JS rejects `-a**2` and requires explicit parens.
// `-3.abs()` is a similar problem, handled differently by different languages.
expr
  : 'this'                                                                      # thisExpr
  | 'outer'                                                                     # outerExpr
  | 'module'                                                                    # moduleExpr
  | 'null'                                                                      # nullLiteral
  | 'true'                                                                      # trueLiteral
  | 'false'                                                                     # falseLiteral
  | IntLiteral                                                                  # intLiteral
  | FloatLiteral                                                                # floatLiteral
  | 'throw' '(' expr err=')'?                                                   # throwExpr
  | 'trace' '(' expr err=')'?                                                   # traceExpr
  | t=('import' | 'import*') '(' stringConstant err=')'?                        # importExpr
  | t=('read' | 'read?' | 'read*') '(' expr err=')'?                            # readExpr
  | Identifier argumentList?                                                    # unqualifiedAccessExpr
  | t=SLQuote singleLineStringPart* t2=SLEndQuote                               # singleLineStringLiteral
  | t=MLQuote multiLineStringPart* t2=MLEndQuote                                # multiLineStringLiteral
  | t='new' type? objectBody                                                    # newExpr
  | expr objectBody                                                             # amendExpr
  | 'super' '.' Identifier argumentList?                                        # superAccessExpr
  | 'super' t='[' e=expr err=']'?                                               # superSubscriptExpr
  | expr t=('.' | '?.') Identifier argumentList?                                # qualifiedAccessExpr
  | l=expr {noNewlineOrSemicolon()}? t='[' r=expr err=']'?                      # subscriptExpr
  | expr '!!'                                                                   # nonNullExpr
  | '-' expr                                                                    # unaryMinusExpr
  | '!' expr                                                                    # logicalNotExpr
  | <assoc=right> l=expr t='**' r=expr                                          # exponentiationExpr
  // for some reason, moving rhs of rules starting with `l=expr` into a
  // separate rule (to avoid repeated parsing of `expr`) messes up precedence
  | l=expr t=('*' | '/' | '~/' | '%') r=expr                                    # multiplicativeExpr
  | l=expr (t='+' | {noNewlineOrSemicolon()}? t='-') r=expr                     # additiveExpr
  | l=expr t=('<' | '>' | '<=' | '>=') r=expr                                   # comparisonExpr
  | l=expr t=('is' | 'as') r=type                                               # typeTestExpr
  | l=expr t=('==' | '!=') r=expr                                               # equalityExpr
  | l=expr t='&&' r=expr                                                        # logicalAndExpr
  | l=expr t='||' r=expr                                                        # logicalOrExpr
  | l=expr t='|>' r=expr                                                        # pipeExpr
  | <assoc=right> l=expr t='??' r=expr                                          # nullCoalesceExpr
  | 'if' '(' c=expr err=')'? l=expr 'else' r=expr                               # ifExpr
  | 'let' '(' parameter '=' l=expr err=')'? r=expr                              # letExpr
  | parameterList '->' expr                                                     # functionLiteral
  | '(' expr err=')'?                                                           # parenthesizedExpr
  ;

objectBody
  : '{' (ps+=parameter (errs+=','? ps+=parameter)* '->')? objectMember* err='}'?
  ;

objectMember
  : modifier* Identifier (typeAnnotation? '=' (v=expr | d='delete') | objectBody+) # objectProperty
  | methodHeader '=' expr                                                          # objectMethod
  | t='[[' k=expr err1=']'? err2=']'? ('=' (v=expr | d='delete') | objectBody+)    # memberPredicate
  | t='[' k=expr err1=']'? err2=']'? ('=' (v=expr | d='delete') | objectBody+)     # objectEntry
  | expr                                                                           # objectElement
  | ('...' | '...?') expr                                                          # objectSpread
  | 'when' '(' e=expr err=')'? (b1=objectBody ('else' b2=objectBody)?)             # whenGenerator
  | 'for' '(' t1=parameter (',' t2=parameter)? 'in' e=expr err=')'? objectBody     # forGenerator
  ;

stringConstant
  : t=SLQuote (ts+=SLCharacters | ts+=SLCharacterEscape | ts+=SLUnicodeEscape)* t2=SLEndQuote
  ;

singleLineStringPart
  : SLInterpolation e=expr ')'
  | (ts+=SLCharacters | ts+=SLCharacterEscape | ts+=SLUnicodeEscape)+
  ;

multiLineStringPart
  : MLInterpolation e=expr ')'
  | (ts+=MLCharacters | ts+=MLNewline | ts+=MLCharacterEscape | ts+=MLUnicodeEscape)+
  ;

// intentionally unused
//TODO: we get a "Mismatched Input" error unless we introduce this parser rule. Why?
reservedKeyword
  : 'protected'
  | 'override'
  | 'record'
  | 'case'
  | 'switch'
  | 'vararg'
  ;
