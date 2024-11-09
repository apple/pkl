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
package org.pkl.core.stdlib.yaml;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.collection.EconomicMap;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.PklConverter;
import org.pkl.core.util.yaml.snake.YamlUtils;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.constructor.StandardConstructor;
import org.snakeyaml.engine.v2.exceptions.Mark;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.*;

public final class ParserNodes {
  private static final Pattern WHITESPACE = Pattern.compile("\\s");

  private ParserNodes() {}

  public abstract static class parse extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self, String text) {
      var uri = "input_string";
      return doParse(self, text, uri);
    }

    @Specialization
    @TruffleBoundary
    protected Object eval(
        VmTyped self, VmTyped resource, @Cached("create()") IndirectCallNode callNode) {
      var text = (String) VmUtils.readMember(resource, Identifier.TEXT, callNode);
      var uri = (String) VmUtils.readMember(resource, Identifier.URI, callNode);
      return doParse(self, text, uri);
    }

    private Object doParse(VmTyped self, String text, String uri) {
      var converter = createConverter(self);
      var load = createLoad(self, text, uri, converter);

      try {
        var document = load.loadFromString(text);
        return converter.convert(document, List.of());
      } catch (YamlEngineException e) {
        if (e.getMessage()
            .startsWith("Number of aliases for non-scalar nodes exceeds the specified")) {
          throw exceptionBuilder()
              .evalError("yamlParseErrorTooManyAliases", getMaxCollectionAliases(self))
              .build();
        }
        throw exceptionBuilder().evalError("yamlParseError").withHint(e.getMessage()).build();
      }
    }
  }

  public abstract static class parseAll extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmList eval(VmTyped self, String text) {
      var uri = "input_string";
      return doParseAll(self, text, uri);
    }

    @Specialization
    @TruffleBoundary
    protected Object eval(
        VmTyped self, VmTyped resource, @Cached("create()") IndirectCallNode callNode) {
      var text = (String) VmUtils.readMember(resource, Identifier.TEXT, callNode);
      var uri = (String) VmUtils.readMember(resource, Identifier.URI, callNode);
      return doParseAll(self, text, uri);
    }

    private VmList doParseAll(VmTyped self, String text, String uri) {
      var converter = createConverter(self);
      var load = createLoad(self, text, uri, converter);
      var builder = VmList.EMPTY.builder();

      try {
        for (var document : load.loadAllFromString(text)) {
          builder.add(converter.convert(document, List.of(VmValueConverter.TOP_LEVEL_VALUE)));
        }
      } catch (YamlEngineException e) {
        if (e.getMessage()
            .startsWith("Number of aliases for non-scalar nodes exceeds the specified")) {
          throw exceptionBuilder()
              .evalError("yamlParseErrorTooManyAliases", getMaxCollectionAliases(self))
              .build();
        }
        throw exceptionBuilder().evalError("yamlParseError").withHint(e.getMessage()).build();
      }

      return builder.build();
    }
  }

  private static PklConverter createConverter(VmTyped self) {
    var converters = (VmMapping) VmUtils.readMember(self, Identifier.CONVERTERS);
    return new PklConverter(converters);
  }

  private static int getMaxCollectionAliases(VmTyped self) {
    var max = (Long) VmUtils.readMember(self, Identifier.MAX_COLLECTION_ALIASES);
    return max.intValue(); // has Pkl type `Int32(isPositive)`
  }

  private static Load createLoad(VmTyped self, String text, String uri, PklConverter converter) {
    var mode = (String) VmUtils.readMember(self, Identifier.MODE);
    var resolver = YamlUtils.getParserResolver(mode);
    var useMapping = (boolean) VmUtils.readMember(self, Identifier.USE_MAPPING);
    var settings =
        LoadSettings.builder()
            .setMaxAliasesForCollections(getMaxCollectionAliases(self))
            .setScalarResolver(resolver)
            .setLabel(uri)
            .build();
    var source =
        Source.newBuilder("pkl", text, uri)
            .mimeType("application/x-yaml")
            .uri(URI.create(uri))
            .cached(false)
            .build();
    return new Load(settings, new Constructor(settings, source, converter, mode, useMapping));
  }

  // Note: We currently use the same [ConstructNode]s for all [YamlParser.mode]s.
  // Using separate nodes might improve performance,
  // and might result in more accurate rejection of invalid nodes with explicit tag.
  private static final class Constructor extends StandardConstructor {
    private final Source source;
    private final PklConverter converter;
    private final boolean useMapping;

    private Deque<Object> currPath = new ArrayDeque<>();

    public Constructor(
        LoadSettings settings,
        Source source,
        PklConverter converter,
        String mode,
        boolean useMapping) {
      super(settings);
      this.source = source;
      this.converter = converter;
      this.useMapping = useMapping;

      currPath.push(VmValueConverter.TOP_LEVEL_VALUE);

      // remove constructors registered by superclass to have full control
      tagConstructors.clear();

      tagConstructors.put(Tag.NULL, new ConstructNull());
      tagConstructors.put(Tag.BOOL, new ConstructBoolean());
      tagConstructors.put(Tag.INT, new ConstructInt(mode.equals("compat") || mode.equals("1.1")));
      tagConstructors.put(Tag.FLOAT, new ConstructFloat());
      tagConstructors.put(Tag.BINARY, new ConstructBinary());
      tagConstructors.put(Tag.SET, new ConstructSet());
      tagConstructors.put(Tag.STR, new ConstructStr());
      tagConstructors.put(Tag.SEQ, new ConstructSeq());
      tagConstructors.put(Tag.MAP, new ConstructMap());

      // Pkl doesn't have a timestamp type, so parse as string
      tagConstructors.put(new Tag(Tag.PREFIX + "timestamp"), new ConstructStr());
    }

    public static class ConstructBoolean implements ConstructNode {
      @Override
      public Object construct(Node node) {
        var value = ((ScalarNode) node).getValue();
        return switch (value) {
          case "true", "True", "TRUE", "on", "On", "ON", "y", "Y", "yes", "Yes", "YES" -> true;
          default -> false;
        };
      }
    }

    public static class ConstructStr implements ConstructNode {
      @Override
      public Object construct(Node node) {
        return ((ScalarNode) node).getValue();
      }
    }

    private static class ConstructNull implements ConstructNode {
      @Override
      public VmNull construct(Node node) {
        return VmNull.withoutDefault();
      }
    }

    private static class ConstructInt implements ConstructNode {
      final boolean enable11Octals;

      ConstructInt(boolean enable11Octals) {
        this.enable11Octals = enable11Octals;
      }

      @Override
      public Long construct(Node node) {
        var value = ((ScalarNode) node).getValue().replace("_", "");

        var firstChar = value.charAt(0);
        var isNegative = firstChar == '-';
        var offset = isNegative || firstChar == '+' ? 1 : 0;

        if (value.contains(":")) {
          return parseBase60(value, isNegative, offset);
        }

        var radix = 10;

        if (value.charAt(offset) == '0' && value.length() > offset + 1) {
          switch (value.charAt(offset + 1)) {
            case 'b' -> {
              radix = 2;
              offset += 2;
            }
            case 'o' -> {
              radix = 8;
              offset += 2;
            }
            case 'x' -> {
              radix = 16;
              offset += 2;
            }
            default -> {
              if (enable11Octals) {
                radix = 8;
                offset += 1;
              }
            }
          }
        }

        var result = Long.parseLong(value, offset, value.length(), radix);
        if (isNegative) result = -result;
        return result;
      }

      private static long parseBase60(String value, boolean isNegative, int offset) {
        var result = 0L;
        var segments = value.substring(offset).split(":");
        for (var segment : segments) {
          var segmentNum = Long.parseLong(segment);
          result = VmSafeMath.add(VmSafeMath.multiply(result, 60), segmentNum);
        }
        if (isNegative) result = -result;
        return result;
      }
    }

    public static class ConstructFloat implements ConstructNode {
      @Override
      public Double construct(Node node) {
        var value = ((ScalarNode) node).getValue().replace("_", "");

        // `.` and `.___` are floats in 1.1; no revision/erratum exists
        if (value.equals(".")) return 0.0;

        if (value.contains(":")) return parseBase60(value);

        return switch (value) {
          case ".nan", ".NaN", ".NAN" -> Double.NaN;
          case ".inf", ".Inf", ".INF", "+.inf", "+.Inf", "+.INF" -> Double.POSITIVE_INFINITY;
          case "-.inf", "-.Inf", "-.INF" -> Double.NEGATIVE_INFINITY;
          default -> Double.valueOf(value);
        };
      }

      private static double parseBase60(String value) {
        var isNegative = value.startsWith("-");
        if (isNegative || value.startsWith("+")) value = value.substring(1);
        var result = 0d;
        var segments = value.split(":");
        for (var segment : segments) {
          var segmentNum = Double.parseDouble(segment);
          result = VmSafeMath.add(VmSafeMath.multiply(result, 60), segmentNum);
        }
        if (isNegative) result = -result;
        return result;
      }
    }

    // Pkl doesn't have a binary type, so parse as base64 string with whitespace removed
    private static class ConstructBinary implements ConstructNode {
      @Override
      public String construct(Node node) {
        var value = ((ScalarNode) node).getValue();
        return WHITESPACE.matcher(value).replaceAll("");
      }
    }

    private class ConstructSeq implements ConstructNode {
      @Override
      public VmListing construct(Node node) {
        var sequenceNode = (SequenceNode) node;
        var size = sequenceNode.getValue().size();
        var members = EconomicMap.<Object, ObjectMember>create(size);

        var result =
            new VmListing(
                VmUtils.createEmptyMaterializedFrame(),
                BaseModule.getListingClass().getPrototype(),
                members,
                size);

        if (!node.isRecursive()) {
          addMembers(sequenceNode, result);
        }
        return result;
      }

      @Override
      public void constructRecursive(Node node, Object data) {
        if (!node.isRecursive()) {
          throw new YamlEngineException("Unexpected recursive sequence structure. Node: " + node);
        }

        addMembers(((SequenceNode) node), (VmListing) data);
      }

      private void addMembers(SequenceNode node, VmListing listing) {
        var members = (EconomicMap<Object, ObjectMember>) listing.getMembers();
        long index = 0;

        currPath.push(VmValueConverter.WILDCARD_ELEMENT);
        for (var childNode : node.getValue()) {
          var sourceSection = createSourceSection(childNode.getStartMark(), childNode.getEndMark());
          var member =
              new ObjectMember(
                  sourceSection, sourceSection, VmModifier.ELEMENT, null, String.valueOf(index));
          member.initConstantValue(converter.convert(constructObject(childNode), currPath));
          members.put(index++, member);
        }
        currPath.pop();
      }
    }

    private class ConstructSet implements ConstructNode {
      @Override
      public VmListing construct(Node node) {
        var mappingNode = (MappingNode) node;
        var size = mappingNode.getValue().size();
        var members = EconomicMap.<Object, ObjectMember>create(size);

        var result =
            new VmListing(
                VmUtils.createEmptyMaterializedFrame(),
                BaseModule.getListingClass().getPrototype(),
                members,
                size);

        if (!node.isRecursive()) {
          addMembers(mappingNode, result);
        }
        return result;
      }

      @Override
      public void constructRecursive(Node node, Object data) {
        if (!node.isRecursive()) {
          throw new YamlEngineException("Unexpected recursive sequence structure. Node: " + node);
        }

        addMembers(((MappingNode) node), (VmListing) data);
      }

      private void addMembers(MappingNode node, VmListing listing) {
        var members = (EconomicMap<Object, ObjectMember>) listing.getMembers();
        long index = 0;

        flattenMapping(node);
        currPath.push(VmValueConverter.WILDCARD_ELEMENT);
        for (var tuple : node.getValue()) {
          var keyNode = tuple.getKeyNode();
          var sourceSection = createSourceSection(keyNode.getStartMark(), keyNode.getEndMark());
          var member =
              new ObjectMember(
                  sourceSection, sourceSection, VmModifier.ELEMENT, null, String.valueOf(index));
          member.initConstantValue(converter.convert(constructObject(keyNode), currPath));
          members.put(index++, member);
        }
        currPath.pop();
      }
    }

    private class ConstructMap implements ConstructNode {
      @Override
      public VmObject construct(Node node) {
        var mappingNode = (MappingNode) node;
        var size = mappingNode.getValue().size();
        var members = EconomicMap.<Object, ObjectMember>create(size);

        VmObject result;
        if (useMapping) {
          result =
              new VmMapping(
                  VmUtils.createEmptyMaterializedFrame(),
                  BaseModule.getMappingClass().getPrototype(),
                  members);
        } else {
          result =
              new VmDynamic(
                  VmUtils.createEmptyMaterializedFrame(),
                  BaseModule.getDynamicClass().getPrototype(),
                  members,
                  0);
        }

        if (!node.isRecursive()) {
          addMembers(mappingNode, result);
        }
        return result;
      }

      @Override
      public void constructRecursive(Node node, Object data) {
        if (!node.isRecursive()) {
          throw new YamlEngineException("Unexpected recursive sequence structure. Node: " + node);
        }

        addMembers(((MappingNode) node), (VmObject) data);
      }

      private void addMembers(MappingNode node, VmObject object) {
        var members = (EconomicMap<Object, ObjectMember>) object.getMembers();
        var valuePath = currPath;
        var keyPath = new ArrayDeque<>();

        flattenMapping(node);
        for (var tuple : node.getValue()) {
          var keyNode = tuple.getKeyNode();
          var valueNode = tuple.getValueNode();
          var sourceSection = createSourceSection(keyNode.getStartMark(), valueNode.getEndMark());
          var headerSection = createSourceSection(keyNode.getStartMark(), keyNode.getEndMark());

          currPath = keyPath;
          var key = constructObject(keyNode);
          var convertedKey = converter.convert(key, currPath);
          currPath = valuePath;

          var memberName =
              convertedKey instanceof String string && !useMapping ? Identifier.get(string) : null;

          var member =
              new ObjectMember(
                  sourceSection,
                  headerSection,
                  memberName == null ? VmModifier.ENTRY : VmModifier.NONE,
                  memberName,
                  "generated");

          currPath.push(
              key instanceof String string
                  ? Identifier.get(string)
                  : VmValueConverter.WILDCARD_PROPERTY);
          var value = constructObject(valueNode);
          var convertedValue = converter.convert(value, currPath);
          member.initConstantValue(convertedValue);
          currPath.pop();

          members.put(memberName != null ? memberName : convertedKey, member);
        }
      }
    }

    @SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused"})
    private SourceSection createSourceSection(Optional<Mark> start, Optional<Mark> end) {
      // TODO: create real source section once https://github.com/oracle/graal/issues/902 has been
      // fixed
      var s = source;
      return VmUtils.unavailableSourceSection();
    }
  }
}
