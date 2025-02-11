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
package org.pkl.core.ast.builder;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.util.List;
import org.pkl.core.PklBugException;
import org.pkl.core.parser.BaseParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.parser.ast.DocComment;
import org.pkl.core.parser.ast.Modifier;
import org.pkl.core.parser.ast.Modifier.ModifierValue;
import org.pkl.core.parser.ast.Node;
import org.pkl.core.parser.ast.StringConstant;
import org.pkl.core.parser.ast.StringConstantPart;
import org.pkl.core.parser.ast.StringConstantPart.ConstantPart;
import org.pkl.core.parser.ast.StringConstantPart.StringEscape;
import org.pkl.core.parser.ast.StringConstantPart.StringUnicodeEscape;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.util.Nullable;

public abstract class AbstractAstBuilder<T> extends BaseParserVisitor<T> {

  protected final Source source;

  protected abstract VmExceptionBuilder exceptionBuilder();

  protected AbstractAstBuilder(Source source) {
    this.source = source;
  }

  protected String doVisitStringConstant(StringConstant expr) {
    return doVisitStringConstant(expr.getStrParts().getParts());
  }

  protected String doVisitStringConstant(List<StringConstantPart> strs) {
    var builder = new StringBuilder();
    for (var part : strs) {
      builder.append(doVisitStringConstantPart(part));
    }
    return builder.toString();
  }

  protected String doVisitStringConstantPart(StringConstantPart part) {
    if (part instanceof ConstantPart cp) {
      return cp.getStr();
    }
    if (part instanceof StringUnicodeEscape ue) {
      var codePoint = parseUnicodeEscapeSequence(ue);
      return Character.toString(codePoint);
    }
    if (part instanceof StringEscape se) {
      return switch (se.getType()) {
        case NEWLINE -> "\n";
        case QUOTE -> "\"";
        case BACKSLASH -> "\\";
        case TAB -> "\t";
        case RETURN -> "\r";
      };
    }
    throw PklBugException.unreachableCode();
  }

  protected int parseUnicodeEscapeSequence(StringUnicodeEscape escape) {
    var text = escape.getEscape();
    var lastIndex = text.length() - 1;
    var startIndex = text.indexOf('{', 2);
    assert startIndex != -1; // guaranteed by lexer
    try {
      return Integer.parseInt(text.substring(startIndex + 1, lastIndex), 16);
    } catch (NumberFormatException e) {
      throw exceptionBuilder()
          .evalError("invalidUnicodeEscapeSequence", text, text.substring(0, startIndex))
          .withSourceSection(createSourceSection(escape))
          .build();
    }
  }

  protected final @Nullable SourceSection createSourceSection(@Nullable Node node) {
    return node == null
        ? null
        : source.createSection(node.span().charIndex(), node.span().length());
  }

  protected SourceSection @Nullable [] createDocSourceSection(@Nullable DocComment node) {
    return createDocSourceSection(source, node);
  }

  protected SourceSection createSourceSection(Span span) {
    return source.createSection(span.charIndex(), span.length());
  }

  protected final SourceSection createSourceSection(
      List<? extends Modifier> modifiers, ModifierValue symbol) {

    var modifierCtx =
        modifiers.stream().filter(mod -> mod.getValue() == symbol).findFirst().orElseThrow();

    return createSourceSection(modifierCtx);
  }

  protected static @Nullable SourceSection createSourceSection(Source source, @Nullable Node node) {
    if (node == null) return null;
    return createSourceSection(source, node.span());
  }

  protected static SourceSection @Nullable [] createDocSourceSection(
      Source source, @Nullable DocComment node) {
    if (node == null) return null;
    var spans = node.getSpans();
    var sections = new SourceSection[spans.size()];
    for (var i = 0; i < sections.length; i++) {
      var span = spans.get(i);
      sections[i] = source.createSection(span.charIndex(), span.length());
    }
    return sections;
  }

  protected static SourceSection createSourceSection(Source source, Span span) {
    return source.createSection(span.charIndex(), span.length());
  }

  protected SourceSection startOf(Node node) {
    return startOf(node.span());
  }

  protected SourceSection startOf(Span span) {
    return source.createSection(span.charIndex(), 1);
  }

  protected SourceSection shrinkLeft(SourceSection section, int length) {
    return source.createSection(section.getCharIndex() + length, section.getCharLength() - length);
  }
}
