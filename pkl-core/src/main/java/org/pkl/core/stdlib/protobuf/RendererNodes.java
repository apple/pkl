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
package org.pkl.core.stdlib.protobuf;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Optional;
import org.pkl.core.DurationUnit;
import org.pkl.core.ast.member.ClassProperty;
import org.pkl.core.ast.member.PropertyTypeNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.TypeNode.BooleanTypeNode;
import org.pkl.core.ast.type.TypeNode.IntSlotTypeNode;
import org.pkl.core.ast.type.TypeNode.IntTypeNode;
import org.pkl.core.ast.type.TypeNode.ListTypeNode;
import org.pkl.core.ast.type.TypeNode.ListingOrMappingTypeNode;
import org.pkl.core.ast.type.TypeNode.ListingTypeNode;
import org.pkl.core.ast.type.TypeNode.MapTypeNode;
import org.pkl.core.ast.type.TypeNode.MappingTypeNode;
import org.pkl.core.ast.type.TypeNode.NonFinalClassTypeNode;
import org.pkl.core.ast.type.TypeNode.NullableTypeNode;
import org.pkl.core.ast.type.TypeNode.ObjectSlotTypeNode;
import org.pkl.core.ast.type.TypeNode.SetTypeNode;
import org.pkl.core.ast.type.TypeNode.StringLiteralTypeNode;
import org.pkl.core.ast.type.TypeNode.StringTypeNode;
import org.pkl.core.ast.type.TypeNode.TypeAliasTypeNode;
import org.pkl.core.ast.type.TypeNode.UnionOfStringLiteralsTypeNode;
import org.pkl.core.ast.type.TypeNode.UnionTypeNode;
import org.pkl.core.ast.type.VmTypeMismatchException;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmDataSize;
import org.pkl.core.runtime.VmDuration;
import org.pkl.core.runtime.VmDynamic;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmIntSeq;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmListing;
import org.pkl.core.runtime.VmMap;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmPair;
import org.pkl.core.runtime.VmRegex;
import org.pkl.core.runtime.VmSet;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.runtime.VmValue;
import org.pkl.core.stdlib.AbstractRenderer;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.PklConverter;
import org.pkl.core.util.ArrayCharEscaper;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.json.JsonEscaper;

public final class RendererNodes {
  // Defined here because only relevant to this class.
  private static final Identifier SECONDS = Identifier.get("seconds");
  private static final Identifier NANOS = Identifier.get("nanos");

  private static final ArrayCharEscaper fieldNameEscaper =
      ArrayCharEscaper.builder().withEscape('.', "_").withEscape('#', "_").build();

  private static final StringTypeNode STRING_TYPE =
      new StringTypeNode(VmUtils.unavailableSourceSection());
  private static final IntTypeNode INT_TYPE = new IntTypeNode(VmUtils.unavailableSourceSection());

  private RendererNodes() {}

  public abstract static class renderDocument extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self, Object value) {
      var builder = new StringBuilder();
      createRenderer(self, builder).renderDocument(value);
      return builder.toString();
    }
  }

  public abstract static class renderValue extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self, Object value) {
      var builder = new StringBuilder();
      createRenderer(self, builder).renderValue(value);
      return builder.toString();
    }
  }

  public abstract static class renderType extends ExternalMethod1Node {
    @SuppressWarnings("unused")
    @Specialization
    protected String eval(VmTyped self, Object value) {
      return renderType(value);
    }
  }

  @TruffleBoundary
  private static String renderType(Object value) {
    var clazz =
        value instanceof VmValue vmValue
            ? vmValue.getVmClass()
            : value instanceof TypeNode typeNode ? typeNode.getVmClass() : value.getClass();
    if (clazz == null) {
      throw new VmExceptionBuilder()
          .evalError("cannotResolveTypeForProtobuf")
          .withProgramValue("Value", value)
          .build();
    }
    var name =
        clazz instanceof VmClass vmClass
            ? vmClass.getSimpleName()
            : ((Class<?>) clazz).getSimpleName();

    return fieldNameEscaper.escape(name);
  }

  @TruffleBoundary
  private static ProtobufRenderer createRenderer(VmTyped self, StringBuilder builder) {
    var indent = (String) VmUtils.readMember(self, Identifier.INDENT);
    var converters = (VmMapping) VmUtils.readMember(self, Identifier.CONVERTERS);

    return new ProtobufRenderer(builder, indent, new PklConverter(converters));
  }

  private static final class ProtobufRenderer extends AbstractRenderer {
    private final Deque<Identifier> propertyPath = new ArrayDeque<>();
    private final Deque<Boolean> wrapperRequirement = new ArrayDeque<>();
    private final JsonEscaper jsonEscaper = new JsonEscaper(false);

    private @Nullable TypeNode collectionType = null;

    public ProtobufRenderer(StringBuilder builder, String indent, PklConverter converter) {
      super("Protobuf", builder, indent, converter, true, true);
    }

    @Override
    protected void visitDocument(Object value) {
      visit(value);
      startNewLine();
    }

    @Override
    protected void visitTopLevelValue(Object value) {
      if (value instanceof VmValue && !(value instanceof VmTyped || value instanceof VmDuration)) {
        var name = value.getClass().getSimpleName();
        if (name.startsWith("Vm")) {
          name = name.substring(2);
        }
        throw new VmExceptionBuilder().evalError("invalidProtobufTopLevelValue", name).build();
      }
      assert propertyPath.isEmpty() && wrapperRequirement.isEmpty() : "Corrupted traversal stack.";
      wrapperRequirement.push(false);
      visit(value);
      var wrap = wrapperRequirement.pop();
      assert !wrap && propertyPath.isEmpty() && wrapperRequirement.isEmpty()
          : "Corrupted traversal stack.";
    }

    @Override
    protected void visitRenderDirective(VmTyped value) {
      writePropertyName();
      // append verbatim
      builder.append(VmUtils.readTextProperty(value));
    }

    @Override
    protected void startDynamic(VmDynamic value) {
      throw new VmExceptionBuilder()
          .evalError("cannotRenderTypeAddConverter", "Dynamic", "Protobuf")
          .withProgramValue("Value", value)
          .build();
    }

    @Override
    protected void endDynamic(VmDynamic value, boolean isEmpty) {}

    @Override
    protected void startTyped(VmTyped value) {
      wrapperRequirement.push(false);
      if (!propertyPath.isEmpty()) {
        writePropertyName();
        startMessage();
      }
    }

    @Override
    protected void endTyped(VmTyped value, boolean isEmpty) {
      var popped = wrapperRequirement.pop();
      assert !popped : "Corrupted traversal stack.";
      if (!propertyPath.isEmpty()) {
        endMessage();
      }
    }

    @Override
    protected void startListing(VmListing value) {
      startWrapper();
    }

    @Override
    protected void endListing(VmListing value, boolean isEmpty) {
      endWrapper();
    }

    @Override
    protected void startMapping(VmMapping value) {
      startMaplike();
    }

    @Override
    protected void endMapping(VmMapping value, boolean isEmpty) {
      endMaplike();
    }

    private void startMaplike() {
      if (requiresWrapper()) {
        writePropertyName();
        propertyPath.push(Identifier.IT);
        startMessage();
      }
      wrapperRequirement.push(true);
    }

    private void endMaplike() {
      wrapperRequirement.pop();
      if (requiresWrapper()) {
        endMessage();
        var popped = propertyPath.pop();
        assert popped == Identifier.IT : "Corrupted traversal stack.";
      }
    }

    @Override
    protected void startList(VmList value) {
      startWrapper();
    }

    @Override
    protected void endList(VmList value) {
      endWrapper();
    }

    @Override
    protected void startSet(VmSet value) {
      startWrapper();
    }

    @Override
    protected void endSet(VmSet value) {
      endWrapper();
    }

    @Override
    protected void startMap(VmMap value) {
      startMaplike();
    }

    @Override
    protected void endMap(VmMap value) {
      endMaplike();
    }

    @Override
    protected void visitElement(long index, Object value, boolean isFirst) {
      if (requiresWrapper()) {
        propertyPath.push(Identifier.IT);
      }
      var typeName = computeName(collectionType, value);
      var addedType = false;
      if (typeName != null) {
        addedType = true;
        writePropertyName();
        startMessage();
        propertyPath.push(Identifier.get("it_" + typeName));
      }
      wrapperRequirement.push(true);
      visit(value);
      var popped = wrapperRequirement.pop();
      assert popped : "Corrupted traversal stack.";
      if (addedType) {
        propertyPath.pop();
        endMessage();
      }
      if (requiresWrapper()) {
        propertyPath.pop();
      }
    }

    @Override
    protected void visitEntryKey(Object key, boolean isFirst) {
      var isDirective = VmUtils.isRenderDirective(key);
      var isValidKey =
          isDirective || key instanceof Long || key instanceof Boolean || key instanceof String;
      if (!isValidKey) {
        throw new VmExceptionBuilder()
            .evalError("cannotRenderNonScalarMapKey")
            .withProgramValue("Key", key)
            .build();
      }
      writePropertyName();
      startMessage();
      startNewLine();
      builder.append("key: ");
      if (isDirective) {
        builder.append(VmUtils.readTextProperty(key));
      } else if (key instanceof String string) {
        builder.append('"').append(jsonEscaper.escape(string)).append('"');
      } else {
        builder.append(key);
      }
    }

    @Override
    protected void visitEntryValue(Object value) {
      propertyPath.push(Identifier.VALUE);
      visit(value);
      propertyPath.pop();
      endMessage();
    }

    private @Nullable String computeName(@Nullable TypeNode it, Object value) {
      if (it instanceof UnionTypeNode unionTypeNode) {
        for (var type : unionTypeNode.getElementTypeNodes()) {
          if (type instanceof UnionTypeNode) {
            var computedName = computeName(type, value);
            if (computedName != null) {
              return computedName;
            }
          } else if (type instanceof NonFinalClassTypeNode) {
            var clazz = type.getVmClass();
            if (value instanceof VmValue vmValue) {
              if (clazz == vmValue.getVmClass()) {
                return clazz.getSimpleName();
              }
            }
          }
          try {
            type.execute(VmUtils.createEmptyMaterializedFrame(), value);
          } catch (VmTypeMismatchException e) {
            continue;
          }
          return renderType(type);
        }
      }
      return null;
    }

    @Override
    protected void visitProperty(Identifier name, Object value, boolean isFirst) {
      var hasWrapper = false;
      var hasCollection = false;
      var prevCollectionType = collectionType;
      Identifier expectedName = null;
      propertyPath.push(name);
      if (enclosingValue instanceof VmTyped) {
        var clazz = value instanceof VmValue vmValue ? vmValue.getVmClass() : value.getClass();
        // Compute the name of the field in the wrapper, based on the type, but only if a wrapper
        // is required.
        var optionalType =
            Optional.of(name)
                .map(enclosingValue.getVmClass()::getProperty)
                .map(ClassProperty::getTypeNode)
                .map(PropertyTypeNode::getTypeNode)
                .map(this::resolveType);
        if (optionalType.isPresent()) {
          var type = optionalType.get();
          var computedName = computeName(type, value);
          if (computedName != null) {
            hasWrapper = true;
            writePropertyName();
            expectedName = Identifier.get("it_" + computedName);
            propertyPath.push(expectedName);
            startMessage();
          } else if (type instanceof ListingOrMappingTypeNode listingOrMappingType) {
            hasCollection = true;
            collectionType = listingOrMappingType.getValueTypeNode();
          } else if (type instanceof ListTypeNode listType) {
            hasCollection = true;
            collectionType = listType.getElementTypeNode();
          } else if (type instanceof MapTypeNode mapType) {
            hasCollection = true;
            collectionType = mapType.getValueTypeNode();
          } else if (type instanceof SetTypeNode setType) {
            hasCollection = true;
            collectionType = setType.getElementTypeNode();
          } else if (type instanceof NonFinalClassTypeNode) {
            if (type.getVmClass() != clazz) {
              throw new VmExceptionBuilder()
                  .evalError("cannotRenderSubtypeForProtobuf", type.getVmClass(), clazz)
                  .build();
            }
          }
        }
      }
      visit(value);
      if (hasWrapper) {
        endMessage();
        var popped = propertyPath.pop();
        assert popped == expectedName : "Corrupted traversal stack.";
      } else if (hasCollection) {
        collectionType = prevCollectionType;
      }
      var popped = propertyPath.pop();
      assert name == popped : "Corrupted traversal stack.";
    }

    private void startNewLine() {
      if (indent.isEmpty() || builder.isEmpty() || builder.charAt(builder.length() - 1) == '\n')
        return;

      builder.append('\n');
      builder.append(currIndent);
    }

    private void startWrapper() {
      if (requiresWrapper()) {
        writePropertyName();
        startMessage();
      }
    }

    private void endWrapper() {
      if (requiresWrapper()) {
        endMessage();
      }
    }

    private boolean requiresWrapper() {
      var result = wrapperRequirement.peek();
      assert result != null : "Corrupted traversal stack.";
      return result;
    }

    private void startMessage() {
      builder.append('{');
      increaseIndent();
    }

    private void endMessage() {
      decreaseIndent();
      if (builder.charAt(builder.length() - 1) != '{') {
        startNewLine();
      }
      builder.append('}');
    }

    private void writePropertyName() {
      if (propertyPath.isEmpty()) return;
      startNewLine();
      var name = propertyPath.peek();
      assert name != null : "Corrupted traversal stack.";
      builder.append(name);
      builder.append(": ");
    }

    @Override
    public void visitString(String value) {
      writePropertyName();
      builder.append('"').append(jsonEscaper.escape(value)).append('"');
    }

    @Override
    public void visitBoolean(Boolean value) {
      writePropertyName();
      builder.append(value);
    }

    @Override
    public void visitInt(Long value) {
      writePropertyName();
      builder.append(value);
    }

    @Override
    public void visitFloat(Double value) {
      writePropertyName();
      builder.append(value);
    }

    @Override
    public void visitDuration(VmDuration value) {
      writePropertyName();
      var nanos = (long) value.convertTo(DurationUnit.NANOS).getValue() % 1000000000;
      var seconds = (long) Math.floor(value.convertTo(DurationUnit.SECONDS).getValue());
      startMessage();

      propertyPath.push(SECONDS);
      writePropertyName();
      builder.append(seconds);
      propertyPath.pop();

      propertyPath.push(NANOS);
      writePropertyName();
      builder.append(nanos);
      propertyPath.pop();

      endMessage();
    }

    @Override
    public void visitDataSize(VmDataSize value) {
      throw new VmExceptionBuilder()
          .evalError("cannotRenderTypeAddConverter", "DataSize", "Protobuf")
          .withProgramValue("Value", value)
          .build();
    }

    @Override
    public void visitIntSeq(VmIntSeq value) {
      writePropertyName();
      builder.append(value);
    }

    @Override
    public void visitPair(VmPair value) {
      writePropertyName();
      builder.append(value);
    }

    @Override
    public void visitRegex(VmRegex value) {
      writePropertyName();
      builder.append(value);
    }

    @Override
    public void visitNull(VmNull value) {
      writePropertyName();
      builder.append(value);
    }

    /**
     * Resolves types for the purpose of protobuf rendering. "Sees through" nullable types and type
     * aliases, simplifies variations of {@code Int} and {@code String} types (literate string
     * types, {@code (U)Int_X_} type aliases, etc.), and checks that map key types are legal for
     * protobuf.
     */
    // TODO: Memoize?
    private @Nullable TypeNode resolveType(@Nullable TypeNode type) {
      if (type instanceof IntTypeNode
          || type instanceof StringTypeNode
          || type instanceof BooleanTypeNode) {
        return type;
      } else if (type instanceof IntSlotTypeNode) {
        return INT_TYPE;
      } else if (type instanceof UnionOfStringLiteralsTypeNode
          || type instanceof StringLiteralTypeNode) {
        return STRING_TYPE;
      } else if (type instanceof NullableTypeNode nullableType) {
        return resolveType(nullableType.getElementTypeNode());
      } else if (type instanceof TypeAliasTypeNode typeAliasType) {
        return resolveType(typeAliasType.getVmTypeAlias().getTypeNode());
      } else if (type instanceof ListingTypeNode listingType) {
        var valueType = resolveType(listingType.getValueTypeNode());
        assert valueType != null : "Failed to resolve type node.";

        type =
            requiresWrapper()
                ? null
                : new ListingTypeNode(VmUtils.unavailableSourceSection(), valueType);
        return type;
      } else if (type instanceof MappingTypeNode mappingType) {
        var keyType = resolveType(mappingType.getKeyTypeNode());
        if (!(keyType instanceof IntTypeNode
            || keyType instanceof StringTypeNode
            || keyType instanceof BooleanTypeNode)) {
          throw new VmExceptionBuilder()
              .evalError("cannotRenderNonScalarMapKeyType")
              .withSourceSection(type.getSourceSection())
              .build();
        }
        var valueType = resolveType(mappingType.getValueTypeNode());
        assert valueType != null : "Incomplete or malformed Mapping type";
        mappingType = new MappingTypeNode(VmUtils.unavailableSourceSection(), keyType, valueType);

        type = requiresWrapper() ? null : mappingType;
        return type;
      } else if (type instanceof UnionTypeNode) {
        // Some non-obvious normalization going on here:
        // - A union type resolves to a union type, unless all element types are string or string
        // literal types.
        // - All element types are resolved also.
        // - All string literal types are combined into a single String case.
        var hasString = false;
        var elements = new ArrayList<TypeNode>();
        for (var t : ((UnionTypeNode) type).getElementTypeNodes()) {
          var resolved = resolveType(t);
          if (resolved instanceof StringLiteralTypeNode || resolved instanceof StringTypeNode) {
            if (!hasString) {
              hasString = true;
              elements.add(new StringTypeNode(VmUtils.unavailableSourceSection()));
            }
          } else {
            elements.add(resolved);
          }
        }
        // Sort classes with lowest-in-hierarchy first, to correctly derive names for oneOf.
        elements.sort(
            (o1, o2) -> {
              if (o1 instanceof ObjectSlotTypeNode && o2 instanceof ObjectSlotTypeNode) {
                var t1 = o1.getVmClass();
                var t2 = o2.getVmClass();
                if (t1 == null || t2 == null) return 0;
                return t1.isSubclassOf(t2) ? -1 : t2.isSubclassOf(t1) ? 1 : 0;
              }
              return 0;
            });

        type =
            hasString && elements.size() == 1
                ? elements.get(0)
                : new UnionTypeNode(
                    VmUtils.unavailableSourceSection(),
                    -1,
                    elements.toArray(new TypeNode[0]),
                    false);
        return type;
      }
      return type;
    }
  }
}
