/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.*;
import java.util.regex.Pattern;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.yaml.snake.YamlUtils;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.StreamDataWriter;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.emitter.Emitter;
import org.snakeyaml.engine.v2.events.*;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.resolver.ScalarResolver;

final class YamlRenderer implements ValueRenderer {
  private final ScalarResolver resolver = YamlUtils.getEmitterResolver("compat");
  private final Visitor visitor = new Visitor();
  private final Emitter emitter;
  private final boolean omitNullProperties;
  private final boolean isStream;

  public YamlRenderer(Writer writer, int indent, boolean omitNullProperties, boolean isStream) {
    var dumpSettings =
        DumpSettings.builder()
            .setIndent(indent)
            .setBestLineBreak("\n")
            .setScalarResolver(resolver)
            .build();

    emitter =
        new Emitter(
            dumpSettings,
            new StreamDataWriter() {
              @Override
              public void write(String str) {
                try {
                  writer.write(str);
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              }

              @Override
              public void write(String str, int off, int len) {
                try {
                  writer.write(str, off, len);
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              }
            });

    this.omitNullProperties = omitNullProperties;
    this.isStream = isStream;
  }

  @Override
  public void renderDocument(Object value) {
    if (isStream) {
      if (!(value instanceof Iterable)) {
        throw new RendererException(
            String.format(
                "The top-level value of a YAML stream must have type `Collection`, but got type `%s`.",
                value.getClass().getTypeName()));
      }
      var iterable = (Iterable<?>) value;
      emitter.emit(new StreamStartEvent());
      for (var elem : iterable) {
        emitter.emit(new DocumentStartEvent(false, Optional.empty(), Map.of()));
        visitor.visit(elem);
        emitter.emit(new DocumentEndEvent(false));
      }
      emitter.emit(new StreamEndEvent());
    } else {
      // a top-level YAML value can have any type
      renderValue(value);
    }
  }

  @Override
  public void renderValue(Object value) {
    emitter.emit(new StreamStartEvent());
    emitter.emit(new DocumentStartEvent(false, Optional.empty(), Map.of()));

    visitor.visit(value);

    emitter.emit(new DocumentEndEvent(false));
    emitter.emit(new StreamEndEvent());
  }

  protected class Visitor implements ValueVisitor {
    @Override
    public void visitString(String value) {
      emitter.emit(YamlUtils.stringScalar(value, resolver));
    }

    @Override
    public void visitInt(Long value) {
      emitter.emit(YamlUtils.plainScalar(value.toString(), Tag.INT));
    }

    @Override
    public void visitFloat(Double value) {
      emitter.emit(YamlUtils.plainScalar(value.toString(), Tag.FLOAT));
    }

    @Override
    public void visitBoolean(Boolean value) {
      emitter.emit(YamlUtils.plainScalar(value.toString(), Tag.BOOL));
    }

    @Override
    public void visitDuration(Duration value) {
      throw new RendererException(
          String.format("Values of type `Duration` cannot be rendered as YAML. Value: %s", value));
    }

    @Override
    public void visitDataSize(DataSize value) {
      throw new RendererException(
          String.format("Values of type `DataSize` cannot be rendered as YAML. Value: %s", value));
    }

    @Override
    public void visitPair(Pair<?, ?> value) {
      doVisitIterable(value, null);
    }

    @Override
    public void visitList(List<?> value) {
      doVisitIterable(value, null);
    }

    @Override
    public void visitSet(Set<?> value) {
      doVisitIterable(value, "!!set");
    }

    @Override
    public void visitMap(Map<?, ?> value) {
      for (var key : value.keySet()) {
        if (!(key instanceof String)) {
          // http://stackoverflow.com/questions/33987316/what-is-a-complex-mapping-key-in-yaml
          throw new RendererException(
              String.format(
                  "Maps with non-String keys cannot currently be rendered as YAML. Key: %s", key));
        }
      }

      @SuppressWarnings("unchecked")
      var mapValue = (Map<String, ?>) value;
      doVisitProperties(mapValue);
    }

    @Override
    public void visitObject(PObject value) {
      doVisitProperties(value.getProperties());
    }

    @Override
    public void visitModule(PModule value) {
      doVisitProperties(value.getProperties());
    }

    @Override
    public void visitClass(PClass value) {
      throw new RendererException(
          String.format(
              "Values of type `Class` cannot be rendered as YAML. Value: %s",
              value.getSimpleName()));
    }

    @Override
    public void visitTypeAlias(TypeAlias value) {
      throw new RendererException(
          String.format(
              "Values of type `TypeAlias` cannot be rendered as YAML. Value: %s",
              value.getSimpleName()));
    }

    @Override
    public void visitNull() {
      emitter.emit(YamlUtils.plainScalar("null", Tag.NULL));
    }

    @Override
    public void visitRegex(Pattern value) {
      throw new RendererException(
          String.format("Values of type `Regex` cannot be rendered as YAML. Value: %s", value));
    }

    private void doVisitIterable(Iterable<?> iterable, @Nullable String tag) {
      emitter.emit(
          new SequenceStartEvent(
              Optional.empty(), Optional.ofNullable(tag), true, FlowStyle.BLOCK));
      for (var elem : iterable) visit(elem);
      emitter.emit(new SequenceEndEvent(Optional.empty(), Optional.empty()));
    }

    private void doVisitProperties(Map<String, ?> properties) {
      emitter.emit(
          new MappingStartEvent(Optional.empty(), Optional.empty(), true, FlowStyle.BLOCK));

      for (var entry : properties.entrySet()) {
        var value = entry.getValue();

        if (omitNullProperties && value instanceof PNull) {
          continue;
        }

        emitter.emit(YamlUtils.stringScalar(entry.getKey(), resolver));
        visit(value);
      }

      emitter.emit(new MappingEndEvent(Optional.empty(), Optional.empty()));
    }
  }
}
