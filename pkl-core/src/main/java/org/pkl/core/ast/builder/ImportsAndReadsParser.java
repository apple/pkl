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
package org.pkl.core.ast.builder;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.module.ResolvedModuleKey;
import org.pkl.core.parser.Parser;
import org.pkl.core.parser.antlr.PklParser.ImportClauseContext;
import org.pkl.core.parser.antlr.PklParser.ImportExprContext;
import org.pkl.core.parser.antlr.PklParser.ModuleExtendsOrAmendsClauseContext;
import org.pkl.core.parser.antlr.PklParser.ReadExprContext;
import org.pkl.core.parser.antlr.PklParser.SingleLineStringLiteralContext;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.Pair;

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
public final class ImportsAndReadsParser
    extends AbstractAstBuilder<@Nullable List<Pair<String, SourceSection>>> {

  /** Parses a module, and collects all imports and reads. */
  public static @Nullable List<Pair<String, SourceSection>> parse(
      ModuleKey moduleKey, ResolvedModuleKey resolvedModuleKey) throws IOException {
    var parser = new Parser();
    var text = resolvedModuleKey.loadSource();
    var source = VmUtils.createSource(moduleKey, text);
    var importListParser = new ImportsAndReadsParser(source);
    return parser.parseModule(text).accept(importListParser);
  }

  public ImportsAndReadsParser(Source source) {
    super(source);
  }

  @Override
  protected VmExceptionBuilder exceptionBuilder() {
    return new VmExceptionBuilder();
  }

  @Override
  public List<Pair<String, SourceSection>> visitModuleExtendsOrAmendsClause(
      ModuleExtendsOrAmendsClauseContext ctx) {
    var importStr = doVisitSingleLineConstantStringPart(ctx.stringConstant().ts);
    var sourceSection = createSourceSection(ctx.stringConstant());
    return Collections.singletonList(Pair.of(importStr, sourceSection));
  }

  @Override
  public List<Pair<String, SourceSection>> visitImportClause(ImportClauseContext ctx) {
    var importStr = doVisitSingleLineConstantStringPart(ctx.stringConstant().ts);
    var sourceSection = createSourceSection(ctx.stringConstant());
    return Collections.singletonList(Pair.of(importStr, sourceSection));
  }

  @Override
  public List<Pair<String, SourceSection>> visitImportExpr(ImportExprContext ctx) {
    var importStr = doVisitSingleLineConstantStringPart(ctx.stringConstant().ts);
    var sourceSection = createSourceSection(ctx.stringConstant());
    return Collections.singletonList(Pair.of(importStr, sourceSection));
  }

  @Override
  public List<Pair<String, SourceSection>> visitReadExpr(ReadExprContext ctx) {
    var expr = ctx.expr();
    if (!(expr instanceof SingleLineStringLiteralContext slCtx)) {
      return Collections.emptyList();
    }
    // best-effort approach; only collect read expressions that are string constants.
    var singleParts = slCtx.singleLineStringPart();
    String importString;
    if (singleParts.isEmpty()) {
      importString = "";
    } else if (singleParts.size() == 1) {
      var ts = singleParts.get(0).ts;
      if (!ts.isEmpty()) {
        importString = doVisitSingleLineConstantStringPart(ts);
      } else {
        return Collections.emptyList();
      }
    } else {
      return Collections.emptyList();
    }
    return Collections.singletonList(Pair.of(importString, createSourceSection(slCtx)));
  }

  @Override
  protected @Nullable List<Pair<String, SourceSection>> aggregateResult(
      @Nullable List<Pair<String, SourceSection>> aggregate,
      @Nullable List<Pair<String, SourceSection>> nextResult) {
    if (aggregate == null || aggregate.isEmpty()) {
      return nextResult;
    }
    if (nextResult == null || nextResult.isEmpty()) {
      return aggregate;
    }
    var ret = new ArrayList<Pair<String, SourceSection>>(aggregate.size() + nextResult.size());
    ret.addAll(aggregate);
    ret.addAll(nextResult);
    return ret;
  }
}
