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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.graalvm.polyglot.Context;
import org.organicdesign.fp.collections.ImMap;
import org.pkl.core.PClassInfo;
import org.pkl.core.PObject;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.StackFrame;
import org.pkl.core.StackFrameTransformer;
import org.pkl.core.ast.ConstantNode;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.MemberNode;
import org.pkl.core.ast.SimpleRootNode;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.builder.AstBuilder;
import org.pkl.core.ast.expression.primary.CustomThisNode;
import org.pkl.core.ast.expression.primary.ThisNode;
import org.pkl.core.ast.member.*;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.module.ModuleKeys;
import org.pkl.core.module.ResolvedModuleKey;
import org.pkl.core.parser.LexParseException;
import org.pkl.core.parser.Parser;
import org.pkl.core.parser.antlr.PklParser.ExprContext;
import org.pkl.core.plugins.PluginManager;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.Nullable;

public final class VmUtils {
  /** See {@link MemberNode#shouldRunTypecheck(VirtualFrame)}. */
  @SuppressWarnings("JavadocReference")
  public static final Object SKIP_TYPECHECK_MARKER = new Object();

  public static final String REPL_TEXT = "repl:text";

  public static final URI REPL_TEXT_URI = URI.create(REPL_TEXT);

  private static final PluginManager PLUGIN_MANAGER = PluginManager.DEFAULT;

  private static final VmEngineManager PKL_ENGINE = new VmEngineManager(PLUGIN_MANAGER);

  private static final Pattern DOC_COMMENT_LINE_START =
      Pattern.compile(
          "(?:^|\n|\r\n?)[ \t\f]*///[ \t\f]?",
          Pattern.UNICODE_CHARACTER_CLASS | Pattern.UNICODE_CASE);

  private static final SourceSection UNAVAILABLE_SOURCE_SECTION =
      Source.newBuilder("pkl", "", "unavailable")
          .mimeType(VmLanguage.MIME_TYPE)
          .uri(URI.create("source:unavailable"))
          .cached(false)
          .build()
          .createUnavailableSection();

  private static final DecimalFormatSymbols ROOT_DECIMAL_FORMAT_SYMBOLS =
      DecimalFormatSymbols.getInstance(Locale.ROOT);

  private VmUtils() {}

  static VmTyped createEmptyModule() {
    return new VmTyped(createEmptyMaterializedFrame(), null, null, EconomicMaps.create());
  }

  @TruffleBoundary
  public static MaterializedFrame createEmptyMaterializedFrame() {
    return Truffle.getRuntime().createMaterializedFrame(new Object[] {null, null});
  }

  public static Context createContext(Runnable initializer) {
    var context = PKL_ENGINE.get().build();
    context.initialize("pkl");
    context.enter();
    try {
      initializer.run();
    } finally {
      context.leave();
    }
    return context;
  }

  public static int countLeadingWhitespace(String str) {
    var idx = 0;
    while (idx < str.length() && Character.isWhitespace(str.charAt(idx))) {
      idx += 1;
    }
    return idx;
  }

  public static String indent(String text, String indent) {
    return text.lines().map(line -> indent + line).collect(Collectors.joining("\n"));
  }

  /** Returns the receiver of the message that was dispatched to the currently executing code. */
  public static @Nullable Object getReceiverOrNull(Frame frame) {
    return frame.getArguments()[0];
  }

  /** Returns the receiver of the message that was dispatched to the currently executing code. */
  public static Object getReceiver(Frame frame) {
    var result = getReceiverOrNull(frame);
    assert result != null;
    return result;
  }

  public static void setReceiver(Frame frame, Object receiver) {
    frame.getArguments()[0] = receiver;
  }

  public static VmObjectLike getObjectReceiver(Frame frame) {
    return (VmObjectLike) getReceiver(frame);
  }

  public static VmTyped getTypedObjectReceiver(Frame frame) {
    return (VmTyped) getReceiver(frame);
  }

  /** Returns the owner of the currently executing code. */
  public static @Nullable VmObjectLike getOwnerOrNull(Frame frame) {
    return (VmObjectLike) frame.getArguments()[1];
  }

  /** Returns the owner of the currently executing code. */
  public static VmObjectLike getOwner(Frame frame) {
    var result = getOwnerOrNull(frame);
    assert result != null;
    return result;
  }

  public static void setOwner(Frame frame, VmObjectLike owner) {
    frame.getArguments()[1] = owner;
  }

  /** Returns a `ObjectMember`'s key while executing the corresponding `MemberNode`. */
  public static Object getMemberKey(Frame frame) {
    return frame.getArguments()[2];
  }

  public static ModuleInfo getModuleInfo(VmObjectLike composite) {
    assert composite.isModuleObject();
    return (ModuleInfo) composite.getExtraStorage();
  }

  public static String readTextProperty(VmObjectLike receiver) {
    return (String) VmUtils.readMember(receiver, Identifier.TEXT);
  }

  public static String readTextProperty(Object receiver) {
    assert receiver instanceof VmObjectLike;
    return (String) VmUtils.readMember((VmObjectLike) receiver, Identifier.TEXT);
  }

  @TruffleBoundary
  public static Object readMember(VmObjectLike receiver, Object memberKey) {
    var result = readMemberOrNull(receiver, memberKey);
    if (result != null) return result;

    throw new VmExceptionBuilder().cannotFindMember(receiver, memberKey).build();
  }

  @TruffleBoundary
  public static @Nullable Object readMemberOrNull(
      VmObjectLike receiver, Object memberKey, boolean checkType) {
    return readMemberOrNull(receiver, memberKey, checkType, IndirectCallNode.getUncached());
  }

  @TruffleBoundary
  public static @Nullable Object readMemberOrNull(
      VmObjectLike receiver, Object memberKey, IndirectCallNode callNode) {
    return readMemberOrNull(receiver, memberKey, true, callNode);
  }

  @TruffleBoundary
  public static @Nullable Object readMemberOrNull(VmObjectLike receiver, Object memberKey) {
    return readMemberOrNull(receiver, memberKey, true, IndirectCallNode.getUncached());
  }

  /**
   * Before calling this method, always try `VmObject.getCachedValue()`. (This method writes to the
   * cache, but doesn't read from it.)
   */
  @TruffleBoundary
  public static Object doReadMember(
      VmObjectLike receiver, VmObjectLike owner, Object memberKey, ObjectMember member) {
    return doReadMember(receiver, owner, memberKey, member, true, IndirectCallNode.getUncached());
  }

  @TruffleBoundary
  public static Object readMember(
      VmObjectLike receiver, Object memberKey, IndirectCallNode callNode) {
    var result = readMemberOrNull(receiver, memberKey, true, callNode);
    if (result != null) return result;

    throw new VmExceptionBuilder()
        .cannotFindMember(receiver, memberKey)
        .withLocation(callNode)
        .build();
  }

  @TruffleBoundary
  public static @Nullable Object readMemberOrNull(
      VmObjectLike receiver, Object memberKey, boolean checkType, IndirectCallNode callNode) {
    assert (!(memberKey instanceof Identifier) || !((Identifier) memberKey).isLocalProp())
        : "Must use ReadLocalPropertyNode for local properties.";

    final var cachedValue = receiver.getCachedValue(memberKey);
    if (cachedValue != null) return cachedValue;

    for (var owner = receiver; owner != null; owner = owner.getParent()) {
      var member = owner.getMember(memberKey);
      if (member == null) continue;
      return doReadMember(receiver, owner, memberKey, member, checkType, callNode);
    }

    return null;
  }

  /**
   * Before calling this method, always try `VmObject.getCachedValue()`. (This method writes to the
   * cache, but doesn't read from it.)
   */
  @TruffleBoundary
  public static Object doReadMember(
      VmObjectLike receiver,
      VmObjectLike owner,
      Object memberKey,
      ObjectMember member,
      boolean checkType,
      IndirectCallNode callNode) {

    final var constantValue = member.getConstantValue();
    if (constantValue != null) {
      // for a property, do a type check
      if (member.isProp()) {
        var property = receiver.getVmClass().getProperty(member.getName());
        if (property != null && property.getTypeNode() != null) {
          var callTarget = property.getTypeNode().getCallTarget();
          try {
            if (checkType) {
              callNode.call(callTarget, receiver, property.getOwner(), constantValue);
            } else {
              callNode.call(
                  callTarget, receiver, property.getOwner(), constantValue, SKIP_TYPECHECK_MARKER);
            }
          } catch (VmException e) {
            CompilerDirectives.transferToInterpreter();
            // A failed property type check looks as follows in the stack trace:
            // x: Int(isPositive)
            // at ...
            // x = -10
            // at ...
            // However, if the value of `x` is a parse-time constant (as in `x = -10`),
            // there's no root node for it and hence no stack trace element.
            // In this case, insert a stack trace element to make the stack trace look the same
            // as in the non-constant case. (Parse-time constants are an internal optimization.)
            e.getInsertedStackFrames()
                .put(
                    callTarget,
                    createStackFrame(member.getBodySection(), member.getQualifiedName()));
            throw e;
          }
        }
      }

      receiver.setCachedValue(memberKey, constantValue);
      return constantValue;
    }

    var callTarget = member.getCallTarget();
    Object computedValue;
    if (checkType) {
      computedValue = callNode.call(callTarget, receiver, owner, memberKey);
    } else {
      computedValue = callNode.call(callTarget, receiver, owner, memberKey, SKIP_TYPECHECK_MARKER);
    }
    receiver.setCachedValue(memberKey, computedValue);
    return computedValue;
  }

  public static @Nullable ObjectMember findMember(VmObjectLike receiver, Object memberKey) {
    for (var owner = receiver; owner != null; owner = owner.getParent()) {
      var member = owner.getMember(memberKey);
      if (member != null) return member;
    }

    return null;
  }

  public static ExpressionNode createThisNode(
      SourceSection sourceSection, boolean isCustomThisScope) {
    return isCustomThisScope ? new CustomThisNode(sourceSection) : new ThisNode(sourceSection);
  }

  public static boolean isRenderDirective(VmValue value) {
    return value.getVmClass() == BaseModule.getRenderDirectiveClass();
  }

  public static boolean isRenderDirective(Object value) {
    return value instanceof VmTyped && isRenderDirective((VmTyped) value);
  }

  public static boolean isPcfRenderDirective(Object value) {
    return value instanceof VmTyped
        && ((VmTyped) value).getVmClass().getPClassInfo() == PClassInfo.PcfRenderDirective;
  }

  @TruffleBoundary
  public static NodeInfo getNodeInfo(Node node) {
    var info = node.getClass().getAnnotation(NodeInfo.class);
    if (info != null) return info;
    throw new VmExceptionBuilder()
        .bug("Node class `%s` is missing a `@NodeInfo` annotation.", node.getClass().getName())
        .build();
  }

  // implements same behavior as AnyNodes#getClass
  public static VmClass getClass(Object value) {
    if (value instanceof VmValue) {
      return ((VmValue) value).getVmClass();
    }
    if (value instanceof String) {
      return BaseModule.getStringClass();
    }
    if (value instanceof Boolean) {
      return BaseModule.getBooleanClass();
    }
    if (value instanceof Long) {
      return BaseModule.getIntClass();
    }
    if (value instanceof Double) {
      return BaseModule.getFloatClass();
    }

    CompilerDirectives.transferToInterpreter();
    throw new VmExceptionBuilder().bug("Unknown Pkl data type `%s`.", value).build();
  }

  @SuppressWarnings("unused")
  public static String getConfigValue(TruffleLanguage.Env env, String name, String defaultValue) {
    var rawValue = env.getConfig().get(name);
    if (rawValue == null) return defaultValue;

    return rawValue.toString();
  }

  public static SourceSection unavailableSourceSection() {
    return UNAVAILABLE_SOURCE_SECTION;
  }

  @TruffleBoundary
  public static ImMap<Object, Object> put(ImMap<Object, Object> map, String key, Object value) {
    return map.assoc(key, value);
  }

  @TruffleBoundary
  public static StringBuilder createBuilder() {
    return new StringBuilder();
  }

  @TruffleBoundary
  public static void appendToBuilder(StringBuilder builder, String string) {
    builder.append(string);
  }

  @TruffleBoundary
  public static String builderToString(StringBuilder builder) {
    return builder.toString();
  }

  public static void checkPositive(long n) {
    if (n < 0) {
      CompilerDirectives.transferToInterpreter();
      throw new VmExceptionBuilder().evalError("expectedPositiveNumber", n).build();
    }
  }

  public static Source loadSource(ResolvedModuleKey resolvedKey) {
    try {
      var text = resolvedKey.loadSource();
      return createSource(resolvedKey.getOriginal(), text);
    } catch (IOException e) {
      throw new VmExceptionBuilder()
          .evalError("ioErrorLoadingModule", resolvedKey.getOriginal().getUri())
          .withCause(e)
          .build();
    }
  }

  public static Source createSource(ModuleKey moduleKey, String text) {
    return Source.newBuilder("pkl", text, moduleKey.getUri().toString())
        .mimeType(VmLanguage.MIME_TYPE)
        .uri(moduleKey.getUri())
        .cached(false)
        .build();
  }

  public static VmException toVmException(
      LexParseException e, String text, URI moduleUri, String moduleName) {
    var source =
        Source.newBuilder("pkl", text, moduleName)
            .mimeType(VmLanguage.MIME_TYPE)
            .uri(moduleUri)
            .cached(false)
            .build();
    return toVmException(e, source, moduleName);
  }

  // wanted to keep Parser/LexParseException API free from
  // Truffle classes (Source), hence put this method here
  public static VmException toVmException(LexParseException e, Source source, String moduleName) {
    int lineStartOffset;
    try {
      lineStartOffset = source.getLineStartOffset(e.getLine());
    } catch (IllegalArgumentException iae) {
      // work around the fact that antlr and truffle disagree on how many lines a file that is
      // ending in a newline has
      lineStartOffset = source.getLineStartOffset(e.getLine() - 1);
    }

    return new VmExceptionBuilder()
        .adhocEvalError(e.getMessage())
        .withSourceSection(
            source.createSection(
                // compute char offset manually to work around
                // https://github.com/graalvm/truffle/issues/184
                lineStartOffset + e.getColumn() - 1, e.getLength()))
        .withMemberName(moduleName)
        .build();
  }

  public static @Nullable String exportDocComment(@Nullable SourceSection docComment) {
    if (docComment == null) return null;

    var builder = new StringBuilder();
    var matcher = DOC_COMMENT_LINE_START.matcher(docComment.getCharacters());
    var firstMatch = true;
    while (matcher.find()) {
      if (firstMatch) {
        matcher.appendReplacement(builder, "");
        firstMatch = false;
      } else {
        matcher.appendReplacement(builder, "\n");
      }
    }
    matcher.appendTail(builder);
    var newLength = builder.length() - 1;
    assert builder.charAt(newLength) == '\n';
    builder.setLength(newLength);
    return builder.toString();
  }

  public static List<PObject> exportAnnotations(List<VmTyped> annotations) {
    var result = new ArrayList<PObject>(annotations.size());
    exportAnnotations(annotations, result);
    return result;
  }

  public static void exportAnnotations(List<VmTyped> annotations, List<PObject> result) {
    for (var annotation : annotations) {
      annotation.force(false);
      result.add((PObject) annotation.export());
    }
  }

  public static List<VmTyped> evaluateAnnotations(
      VirtualFrame frame, ExpressionNode[] annotationNodes) {
    var result = new ArrayList<VmTyped>(annotationNodes.length);
    evaluateAnnotations(frame, annotationNodes, result);
    return result;
  }

  @ExplodeLoop
  public static void evaluateAnnotations(
      VirtualFrame frame, ExpressionNode[] annotationNodes, List<VmTyped> result) {

    for (var annotationNode : annotationNodes) {
      var annotation = (VmTyped) annotationNode.executeGeneric(frame);
      // do not force annotations here because running other code
      // during class resolution will likely cause evaluation cycles
      result.add(annotation);
    }
  }

  public static int codePointOffsetToCharOffset(String string, long codePointOffset) {
    return codePointOffsetToCharOffset(string, codePointOffset, 0);
  }

  @TruffleBoundary
  public static int codePointOffsetToCharOffset(
      String string, long codePointOffset, int startIndex) {
    assert startIndex >= 0;
    assert startIndex <= string.length();

    var length = string.length();
    var charOffset = startIndex;

    while (charOffset < length && codePointOffset > 0) {
      if (Character.isHighSurrogate(string.charAt(charOffset++))
          && charOffset < length
          && !Character.isLowSurrogate(string.charAt(charOffset++))) {
        codePointOffset -= 2;
      } else {
        codePointOffset -= 1;
      }
    }

    return codePointOffset != 0 ? -1 : charOffset;
  }

  @TruffleBoundary
  public static int codePointOffsetFromEndToCharOffset(String string, long codePointOffset) {
    var charOffset = string.length();

    while (charOffset > 0 && codePointOffset > 0) {
      if (Character.isLowSurrogate(string.charAt(--charOffset))
          && charOffset > 0
          && !Character.isHighSurrogate(string.charAt(--charOffset))) {
        codePointOffset -= 2;
      } else {
        codePointOffset -= 1;
      }
    }

    return codePointOffset != 0 ? -1 : charOffset;
  }

  public static DecimalFormat createDecimalFormat(int fractionDigits) {
    var format = new DecimalFormat();
    format.setDecimalFormatSymbols(ROOT_DECIMAL_FORMAT_SYMBOLS);
    format.setMinimumFractionDigits(fractionDigits);
    format.setMaximumFractionDigits(fractionDigits);
    format.setGroupingUsed(false);
    return format;
  }

  /** Creates a constant object property that has no corresponding definition in Pkl code. */
  public static ObjectMember createSyntheticObjectProperty(
      @Nullable Identifier identifier, String qualifiedName, Object constantValue) {
    var member =
        new ObjectMember(
            unavailableSourceSection(),
            unavailableSourceSection(),
            VmModifier.NONE,
            identifier,
            qualifiedName);
    member.initConstantValue(constantValue);
    return member;
  }

  /** Creates a constant object entry that has no corresponding definition in Pkl code. */
  public static ObjectMember createSyntheticObjectEntry(
      String qualifiedName, Object constantValue) {
    var member =
        new ObjectMember(
            unavailableSourceSection(),
            unavailableSourceSection(),
            VmModifier.ENTRY,
            null,
            qualifiedName);
    member.initConstantValue(constantValue);
    return member;
  }

  /** Creates a constant object element that has no corresponding definition in Pkl code. */
  public static ObjectMember createSyntheticObjectElement(
      String qualifiedName, Object constantValue) {
    var member =
        new ObjectMember(
            unavailableSourceSection(),
            unavailableSourceSection(),
            VmModifier.ELEMENT,
            null,
            qualifiedName);
    member.initConstantValue(constantValue);
    return member;
  }

  @SuppressWarnings("DuplicatedCode")
  public static ObjectMember createObjectProperty(
      VmLanguage language,
      SourceSection sourceSection,
      SourceSection headerSection,
      Identifier propertyName,
      String qualifiedName,
      FrameDescriptor descriptor,
      int modifiers,
      ExpressionNode bodyNode,
      @Nullable PropertyTypeNode typeNode) {

    var property =
        new ObjectMember(sourceSection, headerSection, modifiers, propertyName, qualifiedName);

    // can't use ConstantNode for a local typed property
    // because constant type check wouldn't find the property (type)
    var isLocalTyped = property.isLocal() && typeNode != null;

    if (bodyNode instanceof ConstantNode && !isLocalTyped) {
      property.initConstantValue((ConstantNode) bodyNode);
      return property;
    }

    property.initMemberNode(
        typeNode != null
            ? new TypedPropertyNode(language, descriptor, property, bodyNode, typeNode)
            : property.isLocal()
                ? new UntypedObjectMemberNode(language, descriptor, property, bodyNode)
                : TypeCheckedPropertyNodeGen.create(language, descriptor, property, bodyNode));

    return property;
  }

  @SuppressWarnings("DuplicatedCode")
  public static ObjectMember createLocalObjectProperty(
      VmLanguage language,
      SourceSection sourceSection,
      SourceSection headerSection,
      Identifier propertyName,
      String qualifiedName,
      FrameDescriptor descriptor,
      int modifiers,
      ExpressionNode bodyNode,
      @Nullable UnresolvedTypeNode typeNode) {

    var property =
        new ObjectMember(sourceSection, headerSection, modifiers, propertyName, qualifiedName);

    // can't use ConstantNode for a local typed property
    // because constant type check wouldn't find the property (type)
    var isLocalTyped = property.isLocal() && typeNode != null;

    if (bodyNode instanceof ConstantNode && !isLocalTyped) {
      property.initConstantValue((ConstantNode) bodyNode);
      return property;
    }

    property.initMemberNode(
        typeNode != null
            ? new LocalTypedPropertyNode(language, descriptor, property, bodyNode, typeNode)
            : new UntypedObjectMemberNode(language, descriptor, property, bodyNode));

    return property;
  }

  public static TypeNode[] resolveParameterTypes(
      VirtualFrame frame,
      FrameDescriptor descriptor,
      @Nullable UnresolvedTypeNode[] parameterTypeNodes) {

    var resolvedNodes = new TypeNode[parameterTypeNodes.length];

    for (var i = 0; i < parameterTypeNodes.length; i++) {
      var unresolvedNode = parameterTypeNodes[i];
      var typeNode =
          unresolvedNode == null
              ?
              // resolved parameter type nodes are never null because they have
              // another function besides type checking, namely setting frame slot
              new TypeNode.UnknownTypeNode(VmUtils.unavailableSourceSection())
              : unresolvedNode.execute(frame);

      descriptor.setSlotKind(i, typeNode.getFrameSlotKind());
      typeNode.initWriteSlotNode(i);
      resolvedNodes[i] = typeNode;
    }

    return resolvedNodes;
  }

  public static void checkIsInstantiable(VmClass parentClass, @Nullable Node parentNode) {
    if (parentClass.isInstantiable()) return;

    CompilerDirectives.transferToInterpreter();

    if (parentClass.isAbstract()) {
      throw new VmExceptionBuilder()
          .evalError("cannotInstantiateAbstractClass", parentClass)
          .withOptionalLocation(parentNode)
          .build();
    }

    assert parentClass.isExternal();
    throw new VmExceptionBuilder()
        .evalError("cannotInstantiateExternalClass", parentClass)
        .withOptionalLocation(parentNode)
        .build();
  }

  @TruffleBoundary
  public static Pattern compilePattern(String pattern, Node location) {
    try {
      return Pattern.compile(pattern, Pattern.UNICODE_CHARACTER_CLASS | Pattern.UNICODE_CASE);
    } catch (PatternSyntaxException e) {
      throw new VmExceptionBuilder()
          .withLocation(location)
          .evalError("invalidRegexSyntax", pattern, e.getMessage())
          .build();
    }
  }

  @TruffleBoundary
  public static <K, V> K getKey(Map.Entry<K, V> entry) {
    return entry.getKey();
  }

  @TruffleBoundary
  public static <K, V> V getValue(Map.Entry<K, V> entry) {
    return entry.getValue();
  }

  public static String getDisplayUri(SourceSection section, StackFrameTransformer transformer) {
    var sourceUri = section.getSource().getURI();
    var frame =
        new StackFrame(
            sourceUri.toString(),
            null,
            List.of(),
            section.getStartLine(),
            section.getStartColumn(),
            section.getEndLine(),
            section.getEndColumn());
    StackFrame transformed = transformer.apply(frame);
    return transformed.getModuleUri();
  }

  public static String getDisplayUri(URI moduleUri, StackFrameTransformer transformer) {
    var frame = new StackFrame(moduleUri.toString(), null, List.of(), 1, 1, 1, 1);
    StackFrame transformed = transformer.apply(frame);
    return transformed.getModuleUri();
  }

  public static StackFrame createStackFrame(SourceSection section, @Nullable String memberName) {
    var moduleUri = section.getSource().getURI().toString();
    var startLine = section.getStartLine();
    var endLine = section.getEndLine();

    var sourceLines = new ArrayList<String>(endLine - startLine + 1);
    for (var line = startLine; line <= endLine; line++) {
      sourceLines.add(section.getSource().getCharacters(line).toString());
    }

    return new StackFrame(
        moduleUri,
        memberName,
        sourceLines,
        startLine,
        section.getStartColumn(),
        endLine,
        section.getEndColumn());
  }

  private static ExprContext parseExpressionContext(String expression, Source source) {
    try {
      return new Parser().parseExpressionInput(expression).expr();
    } catch (LexParseException e) {
      throw VmUtils.toVmException(e, source, REPL_TEXT);
    }
  }

  public static Object evaluateExpression(
      VmTyped module,
      String expression,
      SecurityManager securityManager,
      ModuleResolver moduleResolver) {
    var syntheticModule = ModuleKeys.synthetic(URI.create(REPL_TEXT), expression);
    ResolvedModuleKey resolvedModule;
    try {
      resolvedModule = syntheticModule.resolve(securityManager);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (SecurityManagerException e) {
      throw new VmExceptionBuilder().withCause(e).build();
    }
    var source = VmUtils.loadSource(resolvedModule);
    var moduleInfo =
        new ModuleInfo(
            source.createSection(0, source.getLength()),
            VmUtils.unavailableSourceSection(),
            null,
            "repl:text",
            syntheticModule,
            resolvedModule,
            false);
    var language = VmLanguage.get(null);
    var builder = new AstBuilder(source, language, moduleInfo, moduleResolver);
    var exprContext = parseExpressionContext(expression, source);
    var exprNode = (ExpressionNode) exprContext.accept(builder);
    var rootNode =
        new SimpleRootNode(
            language, new FrameDescriptor(), exprNode.getSourceSection(), "", exprNode);
    var callNode = Truffle.getRuntime().createIndirectCallNode();
    return callNode.call(rootNode.getCallTarget(), module, module);
  }

  public static int findSlot(VirtualFrame frame, Object identifier) {
    var descriptor = frame.getFrameDescriptor();
    for (int i = 0; i < descriptor.getNumberOfSlots(); i++) {
      if (descriptor.getSlotName(i) == identifier
          && frame.getTag(i) != FrameSlotKind.Illegal.tag
          // Truffle initializes all frame tags as `FrameSlotKind.Object`, instead of
          // `FrameSlotKind.Illegal`. Unevaluated (in a `when` with `false` condition) `for`
          // generators, therefore, leave their bound variables tagged as `Object`, but `null`. If
          // another `for` generator in the same scope binds variables with the same names, they
          // resolve the wrong slot number.
          && (frame.getTag(i) != FrameSlotKind.Object.tag || frame.getObject(i) != null)) {
        return i;
      }
    }
    return -1;
  }

  public static int findAuxiliarySlot(VirtualFrame frame, Object identifier) {
    return frame.getFrameDescriptor().getAuxiliarySlots().getOrDefault(identifier, -1);
  }
}
