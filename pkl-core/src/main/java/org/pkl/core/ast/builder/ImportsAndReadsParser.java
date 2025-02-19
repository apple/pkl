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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.pkl.core.ast.builder.ImportsAndReadsParser.Entry;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.module.ResolvedModuleKey;
import org.pkl.core.parser.Parser;
import org.pkl.core.parser.ParserError;
import org.pkl.core.parser.ast.Expr;
import org.pkl.core.parser.ast.Expr.ImportExpr;
import org.pkl.core.parser.ast.Expr.ReadExpr;
import org.pkl.core.parser.ast.Expr.ReadType;
import org.pkl.core.parser.ast.Expr.SingleLineStringLiteralExpr;
import org.pkl.core.parser.ast.ExtendsOrAmendsClause;
import org.pkl.core.parser.ast.ExtendsOrAmendsClause.Type;
import org.pkl.core.parser.ast.ImportClause;
import org.pkl.core.parser.ast.StringPart.StringChars;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;

/**
 * Collects module uris and resource uris imported within a module.
 *
 * <p>Gathers the following:
 *
 * <ul>
 *   <li>amends/extends URI's
 *   <li>import declarations
 *   <li>import expressions
 *   <li>read expressions
 * </ul>
 */
public class ImportsAndReadsParser extends AbstractAstBuilder<@Nullable List<Entry>> {

  public record Entry(
      boolean isModule,
      boolean isGlob,
      boolean isExtends,
      boolean isAmends,
      String stringValue,
      SourceSection sourceSection) {}

  /** Parses a module, and collects all imports and reads. */
  public static List<Entry> parse(ModuleKey moduleKey, ResolvedModuleKey resolvedModuleKey)
      throws IOException {
    var parser = new Parser();
    var text = resolvedModuleKey.loadSource();
    var source = VmUtils.createSource(moduleKey, text);
    var importListParser = new ImportsAndReadsParser(source);
    try {
      return parser.parseModule(text).accept(importListParser);
    } catch (ParserError e) {
      var moduleName = IoUtils.inferModuleName(moduleKey);
      throw VmUtils.toVmException(e, source, moduleName);
    }
  }

  public ImportsAndReadsParser(Source source) {
    super(source);
  }

  @Override
  protected VmExceptionBuilder exceptionBuilder() {
    return new VmExceptionBuilder();
  }

  @Override
  public @Nullable List<Entry> visitExtendsOrAmendsClause(ExtendsOrAmendsClause decl) {
    var importStr = decl.getUrl().getString();
    var sourceSection = createSourceSection(decl.getUrl());
    return Collections.singletonList(
        new Entry(
            true,
            false,
            decl.getType() == Type.EXTENDS,
            decl.getType() == Type.AMENDS,
            importStr,
            sourceSection));
  }

  @Override
  public List<Entry> visitImportClause(ImportClause imp) {
    var importStr = imp.getImportStr().getString();
    var sourceSection = createSourceSection(imp.getImportStr());
    return Collections.singletonList(
        new Entry(true, imp.isGlob(), false, false, importStr, sourceSection));
  }

  @Override
  public List<Entry> visitImportExpr(ImportExpr expr) {
    var importStr = expr.getImportStr().getString();
    var sourceSection = createSourceSection(expr.getImportStr());
    return Collections.singletonList(
        new Entry(true, expr.isGlob(), false, false, importStr, sourceSection));
  }

  @Override
  public @Nullable List<Entry> visitReadExpr(ReadExpr expr) {
    return doVisitReadExpr(expr.getExpr(), expr.getReadType() == ReadType.GLOB);
  }

  public List<Entry> doVisitReadExpr(Expr expr, boolean isGlob) {
    if (!(expr instanceof SingleLineStringLiteralExpr slStr)) {
      return Collections.emptyList();
    }
    // best-effort approach; only collect read expressions that are string constants.
    String importString;
    var singleParts = slStr.getParts();
    if (singleParts.isEmpty()) {
      importString = "";
    } else if (singleParts.size() == 1 && singleParts.get(0) instanceof StringChars cparts) {
      importString = cparts.getString();
    } else {
      return Collections.emptyList();
    }

    return Collections.singletonList(
        new Entry(false, isGlob, false, false, importString, createSourceSection(slStr)));
  }

  @Override
  protected @Nullable List<Entry> aggregateResult(
      @Nullable List<Entry> aggregate, @Nullable List<Entry> nextResult) {
    if (aggregate == null || aggregate.isEmpty()) {
      return nextResult;
    }
    if (nextResult == null || nextResult.isEmpty()) {
      return aggregate;
    }
    var ret = new ArrayList<Entry>(aggregate.size() + nextResult.size());
    ret.addAll(aggregate);
    ret.addAll(nextResult);
    return ret;
  }

  @Override
  protected List<Entry> defaultValue() {
    return List.of();
  }
}
