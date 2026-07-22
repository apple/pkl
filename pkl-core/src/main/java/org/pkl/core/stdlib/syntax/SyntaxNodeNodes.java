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
import org.jspecify.annotations.Nullable;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.SyntaxModule;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmObjectBuilder;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.stdlib.PklName;

/**
 * Backs {@code pkl.syntax#SyntaxNode.builtNode}.
 *
 * <p>Reconstructs a generic {@code pkl.syntax#Node} tree from a typed syntax node's own fields,
 * ignoring the parse-time {@code node} it may be backed by.
 *
 * <p>The nodes produced here are storage-less.
 */
public final class SyntaxNodeNodes {
  private SyntaxNodeNodes() {}

  @PklName("builtNode")
  public abstract static class builtNode extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self) {
      return build(self);
    }
  }

  private static VmTyped build(VmTyped self) {
    return switch (self.getVmClass().getSimpleName()) {
      case "ModuleNode" -> buildModule(self);
      case "ModuleDeclarationNode" -> buildModuleDeclaration(self);
      case "ImportNode" -> buildImport(self);
      case "ClassNode" -> buildClass(self);
      case "TypeAliasNode" -> buildTypeAlias(self);
      case "ClassBodyNode" -> buildClassBody(self);
      case "ClassPropertyNode" -> buildClassProperty(self);
      case "ClassMethodNode" -> buildClassMethod(self);
      case "ObjectBodyNode" -> buildObjectBody(self);
      case "ObjectPropertyNode" -> buildObjectProperty(self);
      case "ObjectMethodNode" -> buildObjectMethod(self);
      case "ObjectElementNode" ->
          branch("object_element", List.of(build(reqNode(self, "expression"))));
      case "ObjectEntryNode" -> buildObjectEntry(self);
      case "ObjectSpreadNode" ->
          branch(
              "object_spread",
              List.of(
                  terminal(bool(self, "isNullable") ? "...?" : "..."),
                  build(reqNode(self, "expression"))));
      case "MemberPredicateNode" -> buildMemberPredicate(self);
      case "ForGeneratorNode" -> buildForGenerator(self);
      case "WhenGeneratorNode" -> buildWhenGenerator(self);
      case "ThisExprNode" -> leaf("this_expr", "this");
      case "OuterExprNode" -> leaf("outer_expr", "outer");
      case "ModuleExprNode" -> leaf("module_expr", "module");
      case "NullLiteralExprNode" -> leaf("null_expr", "null");
      case "BoolLiteralExprNode" ->
          leaf("bool_literal_expr", Boolean.toString(bool(self, "value")));
      case "IntLiteralExprNode" -> leaf("int_literal_expr", numText(member(self, "value")));
      case "FloatLiteralExprNode" -> leaf("float_literal_expr", numText(member(self, "value")));
      case "SingleLineStringLiteralExprNode" -> buildSingleLineString(self);
      case "MultiLineStringLiteralExprNode" -> buildMultiLineString(self);
      case "UnqualifiedAccessExprNode" -> buildUnqualifiedAccess(self);
      case "QualifiedAccessExprNode" -> buildQualifiedAccess(self);
      case "SubscriptExprNode" ->
          branch(
              "subscript_expr",
              List.of(
                  build(reqNode(self, "receiver")),
                  operatorLeaf("["),
                  build(reqNode(self, "index")),
                  terminal("]")));
      case "SuperAccessExprNode" -> buildSuperAccess(self);
      case "SuperSubscriptExprNode" ->
          branch(
              "super_subscript_expr",
              List.of(
                  terminal("super"), terminal("["), build(reqNode(self, "index")), terminal("]")));
      case "IfExprNode" -> buildIf(self);
      case "LetExprNode" -> buildLet(self);
      case "ThrowExprNode" -> buildCall("throw_expr", "throw", build(reqNode(self, "expression")));
      case "TraceExprNode" -> buildCall("trace_expr", "trace", build(reqNode(self, "expression")));
      case "ImportExprNode" ->
          buildCall(
              "import_expr",
              bool(self, "isGlob") ? "import*" : "import",
              stringCharsNode(str(self, "uri")));
      case "ReadExprNode" ->
          buildCall("read_expr", str(self, "keyword"), build(reqNode(self, "expression")));
      case "NewExprNode" -> buildNew(self);
      case "AmendsExprNode" ->
          branch(
              "amends_expr",
              List.of(build(reqNode(self, "parentExpr")), build(reqNode(self, "body"))));
      case "BinaryOpExprNode" -> buildBinaryOp(self);
      case "UnaryMinusExprNode" ->
          branch("unary_minus_expr", List.of(terminal("-"), build(reqNode(self, "operand"))));
      case "LogicalNotExprNode" ->
          branch("logical_not_expr", List.of(terminal("!"), build(reqNode(self, "operand"))));
      case "NonNullExprNode" ->
          branch("non_null_expr", List.of(build(reqNode(self, "operand")), operatorLeaf("!!")));
      case "FunctionLiteralExprNode" -> buildFunctionLiteral(self);
      case "ParenthesizedExprNode" ->
          branch(
              "parenthesized_expr",
              List.of(
                  terminal("("),
                  branch(
                      "parenthesized_expr_elements",
                      List.of(build(nonNull(optNode(self, "expression"))))),
                  terminal(")")));
      case "UnknownTypeNode" -> leaf("unknown_type", "unknown");
      case "NothingTypeNode" -> leaf("nothing_type", "nothing");
      case "ModuleTypeNode" -> leaf("module_type", "module");
      case "DeclaredTypeNode" -> buildDeclaredType(self);
      case "NullableTypeNode" ->
          branch("nullable_type", List.of(build(reqNode(self, "baseType")), terminal("?")));
      case "UnionTypeNode" ->
          branch(
              "union_type", interleave(buildAll(listMember(self, "members")), () -> terminal("|")));
      case "FunctionTypeNode" -> buildFunctionType(self);
      case "ConstrainedTypeNode" -> buildConstrainedType(self);
      case "ParenthesizedTypeNode" ->
          branch(
              "parenthesized_type",
              List.of(
                  terminal("("),
                  branch(
                      "parenthesized_type_elements",
                      List.of(build(nonNull(optNode(self, "type"))))),
                  terminal(")")));
      case "StringConstantTypeNode" ->
          branch("string_constant_type", List.of(stringCharsNode(str(self, "value"))));
      case "AnnotationNode" -> buildAnnotation(self);
      case "ParameterNode" -> buildParameter(self);
      case "TypeParameterNode" -> buildTypeParameter(self);
      case "IdentifierNode" -> leaf("identifier", str(self, "value"));
      case "QualifiedIdentifierNode" ->
          branch(
              "qualified_identifier",
              interleave(buildAll(listMember(self, "identifiers")), () -> terminal(".")));
      case "DocCommentNode" -> buildDocComment(self);
      default ->
          throw new VmExceptionBuilder()
              .bug("Unexpected syntax node: " + self.getVmClass().getSimpleName())
              .build();
    };
  }

  private static VmTyped buildModule(VmTyped self) {
    var children = new ArrayList<>();
    var declaration = optNode(self, "declaration");
    if (declaration != null) {
      children.add(build(declaration));
    }
    var imports = listMember(self, "imports");
    if (imports.getLength() > 0) {
      children.add(branch("import_list", buildAll(imports)));
    }
    children.addAll(buildAll(listMember(self, "classes")));
    children.addAll(buildAll(listMember(self, "typeAliases")));
    children.addAll(buildAll(listMember(self, "properties")));
    children.addAll(buildAll(listMember(self, "methods")));
    return branch("module", children);
  }

  private static VmTyped buildModuleDeclaration(VmTyped self) {
    var children = docAndAnnotations(self);
    var name = optNode(self, "name");
    var modifiers = listMember(self, "modifiers");
    if (name != null) {
      var definition = new ArrayList<>();
      if (modifiers.getLength() > 0) {
        definition.add(modifierListNode(modifiers));
      }
      definition.add(terminal("module"));
      definition.add(build(name));
      children.add(branch("module_definition", definition));
    } else if (modifiers.getLength() > 0) {
      children.add(modifierListNode(modifiers));
    }
    var amendsUri = member(self, "amendsUri");
    var extendsUri = member(self, "extendsUri");
    if (amendsUri instanceof String uri) {
      children.add(branch("amends_clause", List.of(terminal("amends"), stringCharsNode(uri))));
    } else if (extendsUri instanceof String uri) {
      children.add(branch("extends_clause", List.of(terminal("extends"), stringCharsNode(uri))));
    }
    return branch("module_declaration", children);
  }

  private static VmTyped buildImport(VmTyped self) {
    var children = new ArrayList<>();
    children.add(terminal(bool(self, "isGlob") ? "import*" : "import"));
    children.add(stringCharsNode(str(self, "uri")));
    var alias = optNode(self, "alias");
    if (alias != null) {
      children.add(branch("import_alias", List.of(terminal("as"), build(alias))));
    }
    return branch("import", children);
  }

  private static VmTyped buildClass(VmTyped self) {
    var children = docAndAnnotations(self);
    var header = new ArrayList<>();
    var modifiers = listMember(self, "modifiers");
    if (modifiers.getLength() > 0) {
      header.add(modifierListNode(modifiers));
    }
    header.add(terminal("class"));
    header.add(build(reqNode(self, "identifier")));
    header.addAll(typeParameterListNodes(listMember(self, "typeParameters")));
    var extendsType = optNode(self, "extendsType");
    if (extendsType != null) {
      header.add(branch("class_header_extends", List.of(terminal("extends"), build(extendsType))));
    }
    children.add(branch("class_header", header));
    var body = optNode(self, "body");
    if (body != null) {
      children.add(build(body));
    }
    return branch("class", children);
  }

  private static VmTyped buildTypeAlias(VmTyped self) {
    var children = docAndAnnotations(self);
    var header = new ArrayList<>();
    var modifiers = listMember(self, "modifiers");
    if (modifiers.getLength() > 0) {
      header.add(modifierListNode(modifiers));
    }
    header.add(terminal("typealias"));
    header.add(build(reqNode(self, "identifier")));
    header.addAll(typeParameterListNodes(listMember(self, "typeParameters")));
    header.add(terminal("="));
    children.add(branch("typealias_header", header));
    children.add(branch("typealias_body", List.of(build(reqNode(self, "type")))));
    return branch("typealias", children);
  }

  private static VmTyped buildClassBody(VmTyped self) {
    var members = new ArrayList<>();
    members.addAll(buildAll(listMember(self, "properties")));
    members.addAll(buildAll(listMember(self, "methods")));
    var children = new ArrayList<>();
    children.add(terminal("{"));
    if (!members.isEmpty()) {
      children.add(branch("class_body_elements", members));
    }
    children.add(terminal("}"));
    return branch("class_body", children);
  }

  private static VmTyped buildClassProperty(VmTyped self) {
    var children = docAndAnnotations(self);
    var headerBegin = new ArrayList<>();
    var modifiers = listMember(self, "modifiers");
    if (modifiers.getLength() > 0) {
      headerBegin.add(modifierListNode(modifiers));
    }
    headerBegin.add(build(reqNode(self, "identifier")));
    var header = new ArrayList<>();
    header.add(branch("class_property_header_begin", headerBegin));
    header.addAll(typeAnnotationNodes(optNode(self, "typeAnnotation")));
    children.add(branch("class_property_header", header));
    var value = optNode(self, "value");
    if (value != null) {
      children.add(terminal("="));
      children.add(branch("class_property_body", List.of(build(value))));
    } else {
      children.addAll(buildAll(listMember(self, "objectBodies")));
    }
    return branch("class_property", children);
  }

  private static VmTyped buildClassMethod(VmTyped self) {
    var children = docAndAnnotations(self);
    var header = new ArrayList<>();
    var modifiers = listMember(self, "modifiers");
    if (modifiers.getLength() > 0) {
      header.add(modifierListNode(modifiers));
    }
    header.add(terminal("function"));
    header.add(build(reqNode(self, "identifier")));
    children.add(branch("class_method_header", header));
    children.addAll(typeParameterListNodes(listMember(self, "typeParameters")));
    children.add(parameterListNode(listMember(self, "parameters")));
    children.addAll(typeAnnotationNodes(optNode(self, "returnType")));
    var body = optNode(self, "body");
    if (body != null) {
      children.add(terminal("="));
      children.add(branch("class_method_body", List.of(build(body))));
    }
    return branch("class_method", children);
  }

  private static VmTyped buildObjectBody(VmTyped self) {
    var children = new ArrayList<>();
    children.add(terminal("{"));
    var parameters = listMember(self, "parameters");
    if (parameters.getLength() > 0) {
      var elements = interleave(buildAll(parameters), SyntaxNodeNodes::comma);
      elements.add(terminal("->"));
      children.add(branch("object_parameter_list", elements));
    }
    var members = listMember(self, "members");
    if (members.getLength() > 0) {
      children.add(branch("object_member_list", buildAll(members)));
    }
    children.add(terminal("}"));
    return branch("object_body", children);
  }

  private static VmTyped buildObjectProperty(VmTyped self) {
    var headerBegin = new ArrayList<>();
    var modifiers = listMember(self, "modifiers");
    if (modifiers.getLength() > 0) {
      headerBegin.add(modifierListNode(modifiers));
    }
    headerBegin.add(build(reqNode(self, "identifier")));
    var header = new ArrayList<>();
    header.add(branch("object_property_header_begin", headerBegin));
    header.addAll(typeAnnotationNodes(optNode(self, "typeAnnotation")));
    var children = new ArrayList<>();
    children.add(branch("object_property_header", header));
    var value = optNode(self, "value");
    if (value != null) {
      children.add(terminal("="));
      children.add(branch("object_property_body", List.of(build(value))));
    } else {
      children.addAll(buildAll(listMember(self, "objectBodies")));
    }
    return branch("object_property", children);
  }

  private static VmTyped buildObjectMethod(VmTyped self) {
    var header = new ArrayList<>();
    var modifiers = listMember(self, "modifiers");
    if (modifiers.getLength() > 0) {
      header.add(modifierListNode(modifiers));
    }
    header.add(terminal("function"));
    header.add(build(reqNode(self, "identifier")));
    var children = new ArrayList<>();
    children.add(branch("class_method_header", header));
    children.addAll(typeParameterListNodes(listMember(self, "typeParameters")));
    children.add(parameterListNode(listMember(self, "parameters")));
    children.addAll(typeAnnotationNodes(optNode(self, "returnType")));
    children.add(terminal("="));
    children.add(branch("class_method_body", List.of(build(reqNode(self, "body")))));
    return branch("object_method", children);
  }

  private static VmTyped buildObjectEntry(VmTyped self) {
    var header = new ArrayList<>();
    header.add(terminal("["));
    header.add(build(reqNode(self, "key")));
    header.add(terminal("]"));
    var value = optNode(self, "value");
    if (value != null) {
      header.add(terminal("="));
    }
    var children = new ArrayList<>();
    children.add(branch("object_entry_header", header));
    if (value != null) {
      children.add(build(value));
    } else {
      children.addAll(buildAll(listMember(self, "objectBodies")));
    }
    return branch("object_entry", children);
  }

  private static VmTyped buildMemberPredicate(VmTyped self) {
    var children = new ArrayList<>();
    children.add(terminal("[["));
    children.add(build(reqNode(self, "condition")));
    children.add(terminal("]"));
    children.add(terminal("]"));
    var value = optNode(self, "value");
    if (value != null) {
      children.add(terminal("="));
      children.add(build(value));
    } else {
      children.addAll(buildAll(listMember(self, "objectBodies")));
    }
    return branch("member_predicate", children);
  }

  private static VmTyped buildForGenerator(VmTyped self) {
    var definitionHeader = new ArrayList<>();
    var keyParameter = optNode(self, "keyParameter");
    if (keyParameter == null) {
      definitionHeader.add(build(reqNode(self, "valueParameter")));
    } else {
      definitionHeader.add(build(keyParameter));
      definitionHeader.add(terminal(","));
      definitionHeader.add(build(reqNode(self, "valueParameter")));
    }
    definitionHeader.add(terminal("in"));
    List<Object> definition =
        List.of(
            branch("for_generator_header_definition_header", definitionHeader),
            build(reqNode(self, "iterable")));
    List<Object> header =
        List.of(
            terminal("("), branch("for_generator_header_definition", definition), terminal(")"));
    return branch(
        "for_generator",
        List.of(
            terminal("for"), branch("for_generator_header", header), build(reqNode(self, "body"))));
  }

  private static VmTyped buildWhenGenerator(VmTyped self) {
    var children = new ArrayList<>();
    children.add(terminal("when"));
    children.add(
        branch(
            "when_generator_header",
            List.of(terminal("("), build(reqNode(self, "condition")), terminal(")"))));
    children.add(build(reqNode(self, "thenBody")));
    var elseBody = optNode(self, "elseBody");
    if (elseBody != null) {
      children.add(terminal("else"));
      children.add(build(elseBody));
    }
    return branch("when_generator", children);
  }

  private static VmTyped buildSingleLineString(VmTyped self) {
    var children = new ArrayList<>();
    children.add(terminal("\""));
    children.addAll(buildStringParts(listMember(self, "parts")));
    children.add(terminal("\""));
    return branch("single_line_string_literal_expr", children);
  }

  private static VmTyped buildMultiLineString(VmTyped self) {
    var children = new ArrayList<>();
    children.add(terminal("\"\"\""));
    children.addAll(buildStringParts(listMember(self, "parts")));
    // The formatter uses colStart of the closing `"""` to determine the indentation to strip from
    // each content line.
    var closingSpan = new VmObjectBuilder(1).addProperty(Identifier.COL_START, 1L);
    children.add(
        makeNode("terminal", null, "\"\"\"", closingSpan.toTyped(SyntaxModule.getSpanClass())));
    return branch("multi_line_string_literal_expr", children);
  }

  private static List<Object> buildStringParts(VmList parts) {
    var result = new ArrayList<>();
    for (var i = 0; i < parts.getLength(); i++) {
      result.addAll(buildStringPart((VmTyped) parts.get(i)));
    }
    return result;
  }

  // Mirrors `StringPartNode.toNodes` for each part kind. `StringPartNode` is not a `SyntaxNode`, so
  // it is handled here rather than through `build`.
  private static List<Object> buildStringPart(VmTyped part) {
    return switch (part.getVmClass().getSimpleName()) {
      case "StringCharsNode" -> List.of(leaf("string_chars", str(part, "value")));
      case "StringEscapeNode" -> List.of(leaf("string_escape", str(part, "value")));
      case "StringNewlineNode" -> List.of(typeOnly("string_newline"));
      case "StringInterpolationNode" ->
          List.of(terminal("\\("), build(reqNode(part, "expression")), terminal(")"));
      default ->
          throw new VmExceptionBuilder()
              .bug("Unexpected string-part node: " + part.getVmClass().getSimpleName())
              .build();
    };
  }

  private static VmTyped buildUnqualifiedAccess(VmTyped self) {
    var children = new ArrayList<>();
    children.add(build(reqNode(self, "identifier")));
    var arguments = optList(self, "arguments");
    if (arguments != null) {
      children.add(argumentListNode(arguments));
    }
    return branch("unqualified_access_expr", children);
  }

  private static VmTyped buildQualifiedAccess(VmTyped self) {
    var member = new ArrayList<>();
    member.add(build(reqNode(self, "identifier")));
    var arguments = optList(self, "arguments");
    if (arguments != null) {
      member.add(argumentListNode(arguments));
    }
    return branch(
        "qualified_access_expr",
        List.of(
            build(reqNode(self, "receiver")),
            operatorLeaf(bool(self, "isNullSafe") ? "?." : "."),
            branch("unqualified_access_expr", member)));
  }

  private static VmTyped buildSuperAccess(VmTyped self) {
    var children = new ArrayList<>();
    children.add(terminal("super"));
    children.add(terminal("."));
    children.add(build(reqNode(self, "identifier")));
    var arguments = optList(self, "arguments");
    if (arguments != null) {
      children.add(argumentListNode(arguments));
    }
    return branch("super_access_expr", children);
  }

  private static VmTyped buildIf(VmTyped self) {
    return branch(
        "if_expr",
        List.of(
            branch(
                "if_header",
                List.of(
                    terminal("if"),
                    branch(
                        "if_condition",
                        List.of(
                            terminal("("),
                            branch("if_condition_expr", List.of(build(reqNode(self, "condition")))),
                            terminal(")"))))),
            branch("if_then_expr", List.of(build(reqNode(self, "thenExpr")))),
            terminal("else"),
            branch("if_else_expr", List.of(build(reqNode(self, "elseExpr"))))));
  }

  private static VmTyped buildLet(VmTyped self) {
    return branch(
        "let_expr",
        List.of(
            terminal("let"),
            branch(
                "let_parameter_definition",
                List.of(
                    terminal("("),
                    branch(
                        "let_parameter",
                        List.of(
                            build(reqNode(self, "parameter")),
                            terminal("="),
                            build(reqNode(self, "bindingValue")))),
                    terminal(")"))),
            build(reqNode(self, "body"))));
  }

  private static VmTyped buildNew(VmTyped self) {
    var type = optNode(self, "type");
    var header =
        type == null
            ? List.<Object>of(terminal("new"))
            : List.<Object>of(terminal("new"), build(type));
    return branch("new_expr", List.of(branch("new_header", header), build(reqNode(self, "body"))));
  }

  private static VmTyped buildBinaryOp(VmTyped self) {
    var operator = str(self, "operator");
    var right =
        operator.equals("is") || operator.equals("as")
            ? build(nonNull(optNode(self, "rightType")))
            : build(nonNull(optNode(self, "right")));
    return branch(
        "binary_op_expr", List.of(build(reqNode(self, "left")), operatorLeaf(operator), right));
  }

  private static VmTyped buildFunctionLiteral(VmTyped self) {
    return branch(
        "function_literal_expr",
        List.of(
            parameterListNode(listMember(self, "parameters")),
            terminal("->"),
            branch("function_literal_body", List.of(build(reqNode(self, "body"))))));
  }

  private static VmTyped buildDeclaredType(VmTyped self) {
    var name = build(reqNode(self, "name"));
    var typeArguments = listMember(self, "typeArguments");
    if (typeArguments.getLength() == 0) {
      return branch("declared_type", List.of(name));
    }
    return branch(
        "declared_type",
        List.of(
            name,
            branch(
                "type_argument_list",
                List.of(
                    terminal("<"),
                    branch(
                        "type_argument_list_elements",
                        interleave(buildAll(typeArguments), SyntaxNodeNodes::comma)),
                    terminal(">")))));
  }

  private static VmTyped buildFunctionType(VmTyped self) {
    var parameterTypes = listMember(self, "parameterTypes");
    var parameters =
        parameterTypes.getLength() == 0
            ? List.<Object>of(terminal("("), terminal(")"))
            : List.<Object>of(
                terminal("("),
                branch(
                    "parenthesized_type_elements",
                    interleave(buildAll(parameterTypes), SyntaxNodeNodes::comma)),
                terminal(")"));
    return branch(
        "function_type",
        List.of(
            branch("function_type_parameters", parameters),
            terminal("->"),
            build(reqNode(self, "returnType"))));
  }

  private static VmTyped buildConstrainedType(VmTyped self) {
    return branch(
        "constrained_type",
        List.of(
            build(reqNode(self, "baseType")),
            branch(
                "constrained_type_constraint",
                List.of(
                    terminal("("),
                    branch(
                        "constrained_type_elements",
                        interleave(
                            buildAll(listMember(self, "constraints")), SyntaxNodeNodes::comma)),
                    terminal(")")))));
  }

  private static VmTyped buildAnnotation(VmTyped self) {
    var children = new ArrayList<>();
    children.add(terminal("@"));
    children.add(build(reqNode(self, "type")));
    var body = optNode(self, "body");
    if (body != null) {
      children.add(build(body));
    }
    return branch("annotation", children);
  }

  private static VmTyped buildParameter(VmTyped self) {
    var identifier = optNode(self, "identifier");
    if (identifier == null) {
      return branch("parameter", List.of(terminal("_")));
    }
    var typeAnnotation = optNode(self, "typeAnnotation");
    if (typeAnnotation == null) {
      return branch("parameter", List.of(build(identifier)));
    }
    var children = new ArrayList<>();
    children.add(build(identifier));
    children.addAll(typeAnnotationNodes(typeAnnotation));
    return branch("parameter", children);
  }

  private static VmTyped buildTypeParameter(VmTyped self) {
    var variance = member(self, "variance");
    if (variance instanceof String v) {
      return branch("type_parameter", List.of(terminal(v), build(reqNode(self, "identifier"))));
    }
    return branch("type_parameter", List.of(build(reqNode(self, "identifier"))));
  }

  private static VmTyped buildDocComment(VmTyped self) {
    var lines = listMember(self, "lines");
    var children = new ArrayList<>();
    for (var i = 0; i < lines.getLength(); i++) {
      children.add(leaf("doc_comment_line", "/// " + lines.get(i)));
    }
    return branch("doc_comment", children);
  }

  private static VmTyped buildCall(String type, String keyword, VmTyped inner) {
    return branch(type, List.of(terminal(keyword), terminal("("), inner, terminal(")")));
  }

  // The doc comment (if any) followed by the annotations of a declaration.
  private static List<Object> docAndAnnotations(VmTyped self) {
    var result = new ArrayList<>();
    var docComment = optNode(self, "docComment");
    if (docComment != null) {
      result.add(build(docComment));
    }
    result.addAll(buildAll(listMember(self, "annotations")));
    return result;
  }

  // Node construction helpers

  private static VmTyped modifierListNode(VmList modifiers) {
    var children = new ArrayList<>();
    for (var i = 0; i < modifiers.getLength(); i++) {
      children.add(leaf("modifier", (String) modifiers.get(i)));
    }
    return branch("modifier_list", children);
  }

  // A quoted `string_chars` node for a string constant like `"foo"`.
  private static VmTyped stringCharsNode(String value) {
    return makeNode(
        "string_chars",
        List.of(terminal("\""), terminal(value), terminal("\"")),
        "\"" + value + "\"",
        null);
  }

  private static VmTyped parameterListNode(VmList parameters) {
    if (parameters.getLength() == 0) {
      return branch("parameter_list", List.of(terminal("("), terminal(")")));
    }
    return branch(
        "parameter_list",
        List.of(
            terminal("("),
            branch(
                "parameter_list_elements",
                interleave(buildAll(parameters), SyntaxNodeNodes::comma)),
            terminal(")")));
  }

  private static VmTyped argumentListNode(VmList arguments) {
    if (arguments.getLength() == 0) {
      return branch("argument_list", List.of(terminal("("), terminal(")")));
    }
    return branch(
        "argument_list",
        List.of(
            terminal("("),
            branch(
                "argument_list_elements", interleave(buildAll(arguments), SyntaxNodeNodes::comma)),
            terminal(")")));
  }

  private static List<Object> typeParameterListNodes(VmList typeParameters) {
    if (typeParameters.getLength() == 0) {
      return List.of();
    }
    return List.of(
        branch(
            "type_parameter_list",
            List.of(
                terminal("<"),
                branch(
                    "type_parameter_list_elements",
                    interleave(buildAll(typeParameters), SyntaxNodeNodes::comma)),
                terminal(">"))));
  }

  private static List<Object> typeAnnotationNodes(@Nullable VmTyped type) {
    if (type == null) {
      return List.of();
    }
    return List.of(branch("type_annotation", List.of(terminal(":"), build(type))));
  }

  private static VmTyped comma() {
    return terminal(",");
  }

  // Interleave `items` with fresh separators.
  private static ArrayList<Object> interleave(
      List<Object> items, java.util.function.Supplier<VmTyped> separator) {
    var result = new ArrayList<>(items.isEmpty() ? 0 : items.size() * 2 - 1);
    for (var item : items) {
      if (!result.isEmpty()) {
        result.add(separator.get());
      }
      result.add(item);
    }
    return result;
  }

  private static VmTyped terminal(String text) {
    return leaf("terminal", text);
  }

  private static VmTyped operatorLeaf(String text) {
    return leaf("operator", text);
  }

  private static VmTyped branch(String type, List<Object> children) {
    return makeNode(type, children, null, null);
  }

  private static VmTyped leaf(String type, String text) {
    return makeNode(type, null, text, null);
  }

  private static VmTyped typeOnly(String type) {
    return makeNode(type, null, null, null);
  }

  // Build a generic `Node`, setting only the members that differ from the class
  // defaults (`children` defaults to empty, `text` to null, `span`/`parent` to their defaults).
  private static VmTyped makeNode(
      String type, @Nullable List<Object> children, @Nullable String text, @Nullable VmTyped span) {
    var builder = new VmObjectBuilder(4);
    builder.addProperty(Identifier.TYPE, type);
    if (children != null) {
      builder.addProperty(Identifier.CHILDREN, VmList.create(children.toArray()));
    }
    if (text != null) {
      builder.addProperty(Identifier.TEXT, text);
    }
    if (span != null) {
      builder.addProperty(Identifier.SPAN, span);
    }
    return builder.toTyped(SyntaxModule.getNodeClass());
  }

  // ===============
  // Member readers
  // ===============

  private static List<Object> buildAll(VmList nodes) {
    var result = new ArrayList<>();
    for (var i = 0; i < nodes.getLength(); i++) {
      result.add(build((VmTyped) nodes.get(i)));
    }
    return result;
  }

  private static Object member(VmTyped self, String name) {
    return VmUtils.readMember(self, Identifier.get(name));
  }

  private static VmTyped reqNode(VmTyped self, String name) {
    return (VmTyped) member(self, name);
  }

  private static @Nullable VmTyped optNode(VmTyped self, String name) {
    return member(self, name) instanceof VmTyped node ? node : null;
  }

  private static VmList listMember(VmTyped self, String name) {
    return (VmList) member(self, name);
  }

  private static @Nullable VmList optList(VmTyped self, String name) {
    return member(self, name) instanceof VmList list ? list : null;
  }

  private static String str(VmTyped self, String name) {
    return (String) member(self, name);
  }

  private static boolean bool(VmTyped self, String name) {
    return (Boolean) member(self, name);
  }

  private static VmTyped nonNull(@Nullable VmTyped value) {
    if (value == null) {
      throw new VmExceptionBuilder().evalError("expectedNonNullValue").build();
    }
    return value;
  }

  private static String numText(Object value) {
    return value instanceof String s ? s : value.toString();
  }
}
