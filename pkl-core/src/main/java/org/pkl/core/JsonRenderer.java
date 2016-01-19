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
package org.pkl.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.pkl.core.util.json.JsonWriter;

final class JsonRenderer implements ValueRenderer {
  private final JsonWriter writer;
  private final boolean omitNullProperties;

  public JsonRenderer(Writer writer, String indent, boolean omitNullProperties) {
    this.writer = new JsonWriter(writer);
    this.writer.setIndent(indent);
    this.omitNullProperties = omitNullProperties;
  }

  @Override
  public void renderDocument(Object value) {
    // JSON document can have any top-level value
    // https://stackoverflow.com/a/3833312
    // http://www.ietf.org/rfc/rfc7159.txt
    renderValue(value);
    try {
      writer.newline();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void renderValue(Object value) {
    new Visitor().visit(value);
  }

  private class Visitor implements ValueVisitor {
    @Override
    public void visitString(String value) {
      try {
        writer.value(value);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public void visitInt(Long value) {
      try {
        writer.value(value);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public void visitFloat(Double value) {
      try {
        writer.value(value);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public void visitBoolean(Boolean value) {
      try {
        writer.value(value);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public void visitDuration(Duration value) {
      throw new RendererException(
          String.format("Values of type `Duration` cannot be rendered as JSON. Value: %s", value));
    }

    @Override
    public void visitDataSize(DataSize value) {
      throw new RendererException(
          String.format("Values of type `DataSize` cannot be rendered as JSON. Value: %s", value));
    }

    @Override
    public void visitPair(Pair<?, ?> value) {
      try {
        writer.beginArray();
        visit(value.getFirst());
        visit(value.getSecond());
        writer.endArray();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public void visitList(List<?> value) {
      doVisitCollection(value);
    }

    @Override
    public void visitSet(Set<?> value) {
      doVisitCollection(value);
    }

    @Override
    public void visitMap(Map<?, ?> value) {
      for (var key : value.keySet()) {
        if (!(key instanceof String)) {
          throw new RendererException(
              String.format(
                  "Maps containing non-String keys cannot be rendered as JSON. Key: %s", key));
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
    public void visitNull() {
      try {
        writer.nullValue();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public void visitClass(PClass value) {
      throw new RendererException(
          String.format(
              "Values of type `Class` cannot be rendered as JSON. Value: %s",
              value.getSimpleName()));
    }

    @Override
    public void visitTypeAlias(TypeAlias value) {
      throw new RendererException(
          String.format(
              "Values of type `TypeAlias` cannot be rendered as JSON. Value: %s",
              value.getSimpleName()));
    }

    @Override
    public void visitRegex(Pattern value) {
      throw new RendererException(
          String.format("Values of type `Regex` cannot be rendered as JSON. Value: %s", value));
    }

    private void doVisitCollection(Collection<?> collection) {
      try {
        writer.beginArray();
        for (var elem : collection) visit(elem);
        writer.endArray();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    private void doVisitProperties(Map<String, ?> properties) {
      try {
        writer.beginObject();

        for (var entry : properties.entrySet()) {
          var value = entry.getValue();

          if (omitNullProperties && value instanceof PNull) continue;

          writer.name(entry.getKey());
          visit(value);
        }

        writer.endObject();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
