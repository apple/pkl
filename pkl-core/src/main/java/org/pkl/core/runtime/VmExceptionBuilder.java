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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.pkl.core.StackFrame;
import org.pkl.core.runtime.MemberLookupSuggestions.Candidate.Kind;
import org.pkl.core.runtime.VmException.ProgramValue;
import org.pkl.core.util.Nullable;

/**
 * Error message guidelines:
 *
 * <ul>
 *   <li>Pkl strives to provide a great experience for casual users. Hence it must excel at error
 *       reporting.
 *   <li>Be concrete and concise. Examples and explanations can help but must carry their weight.
 *   <li>The source line is considered an integral part of the error message. Assume it's always
 *       presented to the user. Design the main message accordingly and avoid duplicated
 *       information.
 *   <li>Use correct "natural language" spelling. Most messages should be sentences that start with
 *       an uppercase letter and end with a dot.
 *   <li>The main message should typically be a single line and sentence. Try to make it both easy
 *       to understand for novices and easy to recognize for experienced users. Use {@link
 *       VmExceptionBuilder#withHint} for longer explanations.
 *   <li>Don't include arbitrary-size information in the main message but instead use {@link
 *       VmExceptionBuilder#withProgramValue}.
 *   <li>Use {@link VmExceptionBuilder#withExternalMessage} whenever possible.
 *   <li>Avoid abbreviations (i.e., e.g., etc., ...). Some users will not be familiar with them.
 *   <li>Use backticks for inline code (cf. Markdown). (For identifiers, *emphasis* may be
 *       preferable but isn't currently used. One problem with special formatting is that error
 *       output doesn't always go to a terminal and hence may be rendered verbatim.)
 */
public final class VmExceptionBuilder {

  private @Nullable Object receiver;
  private @Nullable Map<CallTarget, StackFrame> insertedStackFrames;

  public static class MultilineValue {
    private final Iterable<?> lines;

    private MultilineValue(Iterable<?> lines) {
      this.lines = lines;
    }

    public static Object of(Iterable<?> lines) {
      return new MultilineValue(lines);
    }

    public static Object of(Stream<?> lines) {
      return new MultilineValue(lines.collect(Collectors.toList()));
    }

    public String toString() {
      var builder = new StringBuilder();
      var first = true;
      for (var value : lines) {
        if (first) {
          first = false;
        } else {
          builder.append("\n");
        }
        builder.append(value);
      }
      return builder.toString();
    }
  }

  private @Nullable String message;
  private @Nullable Throwable cause;
  private VmException.Kind kind = VmException.Kind.EVAL_ERROR;
  private boolean isExternalMessage;
  private Object[] messageArguments = new Object[0];
  private final List<VmException.ProgramValue> programValues = new ArrayList<>();
  private @Nullable Node location;
  private @Nullable SourceSection sourceSection;
  private @Nullable String memberName;
  private @Nullable String hint;

  public VmExceptionBuilder typeMismatch(Object value, VmClass expectedType) {
    if (value instanceof VmNull) {
      return evalError("typeMismatchValue", expectedType, "null");
    }

    return evalError("typeMismatch", expectedType, VmUtils.getClass(value))
        .withProgramValue("Value", value);
  }

  public VmExceptionBuilder typeMismatch(
      Object value, VmClass expectedType1, VmClass expectedType2) {
    if (value instanceof VmNull) {
      return evalError("typeMismatchValue2", expectedType1, expectedType2, "null");
    }

    return evalError("typeMismatch2", expectedType1, expectedType2, VmUtils.getClass(value))
        .withProgramValue("Value", value);
  }

  public VmExceptionBuilder unreachableCode() {
    return bug("Unreachable code.");
  }

  public VmExceptionBuilder undefinedValue() {
    kind = VmException.Kind.UNDEFINED_VALUE;
    return withExternalMessage("undefinedValue");
  }

  public VmExceptionBuilder undefinedPropertyValue(Identifier propertyName, Object receiver) {
    kind = VmException.Kind.UNDEFINED_VALUE;
    this.receiver = receiver;
    return withExternalMessage("undefinedPropertyValue", propertyName);
  }

  public VmExceptionBuilder cannotFindMember(VmObjectLike receiver, Object memberKey) {
    if (memberKey instanceof Identifier identifier) {
      return cannotFindProperty(receiver, identifier, true, false);
    }
    if (memberKey instanceof String string) {
      var candidates = KeyLookupSuggestions.forObject(receiver, string);
      if (!candidates.isEmpty()) {
        return evalError(
            "cannotFindKeyListCandidates",
            new ProgramValue("", memberKey),
            new MultilineValue(candidates));
      }
    }
    return evalError("cannotFindKey", new ProgramValue("", memberKey));
  }

  public VmExceptionBuilder cannotFindProperty(
      VmObjectLike receiver,
      Identifier propertyName,
      // true -> property read, false -> property definition
      boolean isRead,
      boolean isImplicitReceiver) {

    var memberKinds = isRead ? EnumSet.allOf(Kind.class) : EnumSet.of(Kind.PROPERTY);

    var candidates =
        new MemberLookupSuggestions(receiver, propertyName, -1, memberKinds)
            .find(isImplicitReceiver);

    if (candidates.isEmpty()) {
      if (isImplicitReceiver) {
        // probably wouldn't be useful to list all members in scope (includes all pkl.base members)
        evalError("cannotFindPropertyInScope", propertyName);
      } else if (receiver.isModuleObject()) {
        evalError(
            "cannotFindPropertyInModule",
            propertyName,
            VmUtils.getModuleInfo(receiver).getModuleName(),
            new MultilineValue(collectPropertyNames(receiver, isRead)));
      } else {
        var propertyNames = collectPropertyNames(receiver, isRead);
        evalError(
            "cannotFindPropertyInObject",
            propertyName,
            receiver.getVmClass(),
            propertyNames.isEmpty() ? "(none)" : new MultilineValue(propertyNames));
      }
    } else {
      if (isImplicitReceiver) {
        evalError(
            "cannotFindPropertyInScopeListCandidates",
            propertyName,
            new MultilineValue(candidates));
      } else if (receiver.isModuleObject()) {
        evalError(
            "cannotFindPropertyInModuleListCandidates",
            propertyName,
            VmUtils.getModuleInfo(receiver).getModuleName(),
            new MultilineValue(candidates));
      } else {
        evalError(
            "cannotFindPropertyInObjectListCandidates",
            propertyName,
            receiver.getVmClass(),
            new MultilineValue(candidates));
      }
    }

    return this;
  }

  // `foo.bar()`
  public VmExceptionBuilder cannotFindMethod(
      VmObjectLike receiver, Identifier methodName, int arity, boolean isImplicitReceiver) {

    var candidates =
        new MemberLookupSuggestions(receiver, methodName, arity, EnumSet.allOf(Kind.class))
            .find(isImplicitReceiver);

    if (candidates.isEmpty()) {
      if (isImplicitReceiver) {
        // probably not useful to list all members in scope (includes all pkl.base members)
        evalError("cannotFindMethodInScope", methodName);
      } else if (receiver.isModuleObject()) {
        evalError(
            "cannotFindMethodInModule",
            methodName,
            VmUtils.getModuleInfo(receiver).getModuleName(),
            new MultilineValue(collectMethodNames(receiver.getVmClass())));
      } else {
        evalError(
            "cannotFindMethodInClass",
            methodName,
            receiver.getVmClass(),
            new MultilineValue(collectMethodNames(receiver.getVmClass())));
      }
    } else {
      if (isImplicitReceiver) {
        evalError(
            "cannotFindMethodInScopeListCandidates", methodName, new MultilineValue(candidates));
      } else if (receiver.isModuleObject()) {
        evalError(
            "cannotFindMethodInModuleListCandidates",
            methodName,
            VmUtils.getModuleInfo(receiver).getModuleName(),
            new MultilineValue(candidates));
      } else {
        evalError(
            "cannotFindMethodInClassListCandidates",
            methodName,
            receiver.getVmClass(),
            new MultilineValue(candidates));
      }
    }

    return this;
  }

  public VmExceptionBuilder cannotFindKey(VmMap map, Object key) {
    if (key instanceof String string) {
      var candidates = KeyLookupSuggestions.forMap(map, string);
      if (!candidates.isEmpty()) {
        return evalError(
            "cannotFindKeyListCandidates",
            new ProgramValue("", key),
            new MultilineValue(candidates));
      }
    }

    return evalError("cannotFindKey", new ProgramValue("", key));
  }

  // not internationalized
  public VmExceptionBuilder bug(String message, Object... args) {
    kind = VmException.Kind.BUG;
    return withMessage(message, args);
  }

  private VmExceptionBuilder withMessage(String message, Object... args) {
    this.message = message;
    messageArguments = args;
    isExternalMessage = false;

    return this;
  }

  private VmExceptionBuilder withExternalMessage(String message, Object... args) {
    this.message = message;
    messageArguments = args;
    isExternalMessage = true;

    return this;
  }

  // slow path, hence no need to cache anything (also resource bundles are cached by default)
  public VmExceptionBuilder evalError(String messageKey, Object... args) {
    return withExternalMessage(messageKey, args);
  }

  public VmExceptionBuilder adhocEvalError(String message, Object... args) {
    return withMessage(message, args);
  }

  public VmExceptionBuilder withProgramValue(String name, Object value) {
    programValues.add(new ProgramValue(name, value));
    return this;
  }

  public VmExceptionBuilder withLocation(Node location) {
    this.location = location;
    withSourceSection(location.getSourceSection());
    return this;
  }

  public VmExceptionBuilder withOptionalLocation(@Nullable Node location) {
    if (location != null) withLocation(location);
    return this;
  }

  public VmExceptionBuilder withSourceSection(@Nullable SourceSection sourceSection) {
    this.sourceSection = sourceSection;
    return this;
  }

  public VmExceptionBuilder withMemberName(String memberName) {
    this.memberName = memberName;
    return this;
  }

  public VmExceptionBuilder withCause(Throwable cause) {
    this.cause = cause;
    if (this.message == null) {
      var causeMessage = cause.getMessage();
      this.message = causeMessage == null ? "None (cause has no message)" : causeMessage;
    }
    return this;
  }

  public VmExceptionBuilder withHint(@Nullable String hint) {
    this.hint = hint;
    return this;
  }

  public VmExceptionBuilder withInsertedStackFrames(
      Map<CallTarget, StackFrame> insertedStackFrames) {
    this.insertedStackFrames = insertedStackFrames;
    return this;
  }

  public VmException build() {
    if (message == null) {
      throw new IllegalStateException("No message set.");
    }

    var effectiveInsertedStackFrames =
        insertedStackFrames == null ? new HashMap<CallTarget, StackFrame>() : insertedStackFrames;
    return switch (kind) {
      case EVAL_ERROR ->
          new VmEvalException(
              message,
              cause,
              isExternalMessage,
              messageArguments,
              programValues,
              location,
              sourceSection,
              memberName,
              hint,
              effectiveInsertedStackFrames);
      case UNDEFINED_VALUE ->
          new VmUndefinedValueException(
              message,
              cause,
              isExternalMessage,
              messageArguments,
              programValues,
              location,
              sourceSection,
              memberName,
              hint,
              receiver,
              effectiveInsertedStackFrames);
      case BUG ->
          new VmBugException(
              message,
              cause,
              isExternalMessage,
              messageArguments,
              programValues,
              location,
              sourceSection,
              memberName,
              hint,
              effectiveInsertedStackFrames);
    };
  }

  private List<Identifier> collectPropertyNames(VmObjectLike object, boolean isRead) {
    var result = new HashSet<Identifier>();
    object.iterateMembers(
        (key, member) -> {
          if (member.isProp() && (isRead || !member.isExternal())) {
            result.add(member.getName());
          }
          return true;
        });
    return result.stream().sorted().collect(Collectors.toList());
  }

  private List<Identifier> collectMethodNames(VmClass clazz) {
    var result = new ArrayList<Identifier>();
    clazz.getMethods().forEach(method -> result.add(method.getName()));
    result.sort(Comparator.naturalOrder());
    return result;
  }
}
