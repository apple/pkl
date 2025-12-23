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
package org.pkl.core.ast.type;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import java.util.*;
import java.util.stream.Collectors;
import org.pkl.core.StackFrame;
import org.pkl.core.ValueFormatter;
import org.pkl.core.ast.type.TypeNode.UnionTypeNode;
import org.pkl.core.runtime.*;
import org.pkl.core.runtime.VmException.ProgramValue;
import org.pkl.core.util.AnsiStringBuilder;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.Nullable;

/**
 * Indicates that a type check failed. [TypeNode]s use this exception instead of [VmException] to
 * make type checking of union types efficient. Note that any [TruffleBoundary] between throw and
 * catch location of this exception must set `transferToInterpreterOnException = false`. (Currently
 * there aren't any.)
 */
public abstract class VmTypeMismatchException extends ControlFlowException {

  protected final SourceSection sourceSection;
  protected final Object actualValue;
  protected @Nullable Map<CallTarget, StackFrame> insertedStackFrames = null;

  protected VmTypeMismatchException(SourceSection sourceSection, Object actualValue) {
    this.sourceSection = sourceSection;
    this.actualValue = actualValue;
  }

  @TruffleBoundary
  public void putInsertedStackFrame(CallTarget callTarget, StackFrame stackFrame) {
    if (this.insertedStackFrames == null) {
      this.insertedStackFrames = new HashMap<>();
    }
    this.insertedStackFrames.put(callTarget, stackFrame);
  }

  @TruffleBoundary
  public abstract void describe(
      AnsiStringBuilder builder, String indent, boolean withPowerAssertions);

  @TruffleBoundary
  public abstract VmException toVmException();

  protected VmExceptionBuilder exceptionBuilder() {
    var builder = new VmExceptionBuilder();
    if (insertedStackFrames != null) {
      builder.withInsertedStackFrames(insertedStackFrames);
    }
    return builder;
  }

  public static final class Simple extends VmTypeMismatchException {

    private final Object expectedType;

    public Simple(SourceSection sourceSection, Object actualValue, Object expectedType) {
      super(sourceSection, actualValue);

      assert expectedType instanceof VmClass
          || expectedType instanceof VmTypeAlias
          || expectedType instanceof String // string literal type
          || expectedType instanceof Set; // union of string literal types

      this.expectedType = expectedType;
    }

    @Override
    @TruffleBoundary
    public void describe(AnsiStringBuilder builder, String indent, boolean withPowerAssertions) {
      String renderedType;
      var valueFormatter = ValueFormatter.basic();
      if (expectedType instanceof String string) {
        // string literal type
        renderedType = valueFormatter.formatStringValue(string, "");
      } else if (expectedType instanceof Set) {
        // union of string literal types
        @SuppressWarnings("unchecked")
        var stringLiterals = (Set<String>) expectedType;
        renderedType =
            stringLiterals.stream()
                .map((l) -> valueFormatter.formatStringValue(l, ""))
                .collect(Collectors.joining("|"));
      } else {
        renderedType = expectedType.toString();
      }

      if (actualValue instanceof VmNull
          || actualValue instanceof String && expectedType instanceof Set) {
        builder.append(
            ErrorMessages.createIndented(
                "typeMismatchValue", indent, renderedType, new ProgramValue("", actualValue)));
        return;
      }

      // give better error than "expected foo.Bar, but got foo.Bar" in case of naming conflict
      if (actualValue instanceof VmTyped actualObj
          && expectedType instanceof VmClass expectedClass) {
        var actualClass = actualObj.getVmClass();
        if (actualClass.getQualifiedName().equals(expectedClass.getQualifiedName())) {
          var actualModuleUri = actualClass.getModule().getModuleInfo().getModuleKey().getUri();
          var expectedModuleUri = expectedClass.getModule().getModuleInfo().getModuleKey().getUri();

          builder
              .append(
                  ErrorMessages.createIndented(
                      actualClass.getPClassInfo().isModuleClass()
                          ? "typeMismatchVersionConflict1"
                          : "typeMismatchVersionConflict2",
                      indent,
                      renderedType,
                      expectedModuleUri,
                      actualModuleUri))
              .append("\n");
          return;
        }
      }

      builder
          .append(
              ErrorMessages.createIndented(
                  "typeMismatch", indent, renderedType, VmUtils.getClass(actualValue)))
          .append("\n")
          .append(indent)
          .append("Value: ")
          .append(VmValueRenderer.singleLine(80 - indent.length()).render(actualValue));
    }

    @Override
    @TruffleBoundary
    public VmException toVmException() {
      return exceptionBuilder().build();
    }

    @Override
    protected VmExceptionBuilder exceptionBuilder() {
      var builder = new AnsiStringBuilder(true);
      describe(builder, "", false);

      return super.exceptionBuilder()
          .adhocEvalError(builder.toString())
          .withSourceSection(sourceSection);
    }
  }

  public static final class Constraint extends VmTypeMismatchException {

    private final SourceSection constraintBodySourceSection;
    private final Map<Node, List<Object>> trackedValues;

    public Constraint(
        SourceSection sourceSection,
        Object actualValue,
        SourceSection constraintBodySourceSection,
        Map<Node, List<Object>> trackedValues) {
      super(sourceSection, actualValue);
      this.constraintBodySourceSection = constraintBodySourceSection;
      this.trackedValues = trackedValues;
    }

    @Override
    @TruffleBoundary
    public void describe(AnsiStringBuilder builder, String indent, boolean withPowerAssertions) {
      var expression = sourceSection.getCharacters().toString();
      builder
          .append(ErrorMessages.createIndented("typeConstraintViolated", indent, expression))
          .append("\n")
          .append(indent)
          .append("Value: ")
          .append(VmValueRenderer.singleLine(80 - indent.length()).render(actualValue));
      if (withPowerAssertions) {
        builder.append("\n\n");
        PowerAssertions.render(
            builder, indent + "    ", constraintBodySourceSection, trackedValues, null);
      }
    }

    @Override
    @TruffleBoundary
    public VmException toVmException() {
      return exceptionBuilder().build();
    }

    @Override
    protected VmExceptionBuilder exceptionBuilder() {
      return super.exceptionBuilder()
          .withMessageRenderer(
              (builder, withPowerAssertions) -> describe(builder, "", withPowerAssertions))
          .withSourceSection(sourceSection);
    }
  }

  public static final class Union extends VmTypeMismatchException {
    private final UnionTypeNode typeCheckNode;
    private final VmTypeMismatchException[] children;

    public Union(
        SourceSection sourceSection,
        Object actualValue,
        UnionTypeNode typeCheckNode,
        VmTypeMismatchException[] children) {
      super(sourceSection, actualValue);
      this.typeCheckNode = typeCheckNode;
      this.children = children;
    }

    @Override
    @TruffleBoundary
    public void describe(AnsiStringBuilder builder, String indent, boolean withPowerAssertions) {
      describeSummary(builder, indent);
      describeDetails(builder, indent);
    }

    @Override
    @TruffleBoundary
    public VmException toVmException() {
      return exceptionBuilder().build();
    }

    @Override
    protected VmExceptionBuilder exceptionBuilder() {
      var summary = new AnsiStringBuilder(true);
      describeSummary(summary, "");

      var details = new AnsiStringBuilder(true);
      describeDetails(details, "");

      return super.exceptionBuilder()
          .adhocEvalError(summary.toString())
          .withSourceSection(sourceSection)
          .withHint(details.toString());
    }

    private void describeSummary(AnsiStringBuilder builder, String indent) {
      var nonTrivialMismatches = findNonTrivialMismatches();

      if (nonTrivialMismatches.isEmpty()) {
        if (actualValue instanceof VmNull) {
          builder.append(
              ErrorMessages.createIndented(
                  "typeMismatchValue", indent, sourceSection.getCharacters().toString(), "null"));
        } else {
          builder.append(
              ErrorMessages.createIndented(
                  "typeMismatch",
                  indent,
                  sourceSection.getCharacters().toString(),
                  VmUtils.getClass(actualValue)));
        }
      } else {
        builder.append(
            ErrorMessages.createIndented(
                "typeMismatchDifferent",
                indent,
                sourceSection.getCharacters().toString(),
                VmUtils.getClass(actualValue)));
      }

      builder
          .append("\n")
          .append(indent)
          .append("Value: ")
          .append(VmValueRenderer.singleLine(80 - indent.length()).render(actualValue));
    }

    private void describeDetails(AnsiStringBuilder builder, String indent) {
      var nonTrivialMismatches = findNonTrivialMismatches();

      var isPeerError = false;
      for (var idx : nonTrivialMismatches) {
        if (!indent.isEmpty() || isPeerError) {
          builder.append("\n\n");
        }
        isPeerError = true;
        builder
            .append(
                ErrorMessages.createIndented(
                    "typeMismatchBecause",
                    indent,
                    typeCheckNode
                        .elementTypeNodes[idx]
                        .getSourceSection()
                        .getCharacters()
                        .toString()))
            .append("\n");
        children[idx].describe(builder, indent + "  ", false);
      }
    }

    private List<Integer> findNonTrivialMismatches() {
      var result = new ArrayList<Integer>();
      for (int idx = 0; idx < children.length; idx++) {
        VmTypeMismatchException child = children[idx];
        if (!(child instanceof VmTypeMismatchException.Simple)
            // identity comparison is intentional
            || child.sourceSection != typeCheckNode.elementTypeNodes[idx].getSourceSection()) {
          result.add(idx);
        }
      }
      return result;
    }
  }

  public static final class Nothing extends VmTypeMismatchException {

    public Nothing(SourceSection sourceSection, Object actualValue) {
      super(sourceSection, actualValue);
    }

    @Override
    @TruffleBoundary
    public void describe(AnsiStringBuilder builder, String indent, boolean withPowerAssertions) {
      builder
          .append(
              ErrorMessages.createIndented(
                  "cannotAssignToNothing", indent, VmUtils.getClass(actualValue)))
          .append("\n")
          .append(indent)
          .append("Value: ")
          .append(VmValueRenderer.singleLine(80 - indent.length()).render(actualValue));
    }

    @Override
    @TruffleBoundary
    public VmException toVmException() {
      return exceptionBuilder().build();
    }

    @Override
    protected VmExceptionBuilder exceptionBuilder() {
      var builder = new AnsiStringBuilder(true);
      describe(builder, "", false);

      return super.exceptionBuilder()
          .adhocEvalError(builder.toString())
          .withSourceSection(sourceSection);
    }
  }
}
