/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.properties.PropertiesUtils;

// To instantiate this class, use ValueRenderers.properties().
final class PropertiesRenderer implements ValueRenderer {
  private final Writer writer;
  private final boolean omitNullProperties;
  private final boolean restrictCharset;

  public PropertiesRenderer(Writer writer, boolean omitNullProperties, boolean restrictCharset) {
    this.writer = writer;
    this.omitNullProperties = omitNullProperties;
    this.restrictCharset = restrictCharset;
  }

  @Override
  public void renderDocument(Object value) {
    new Visitor().renderDocument(value);
  }

  @Override
  public void renderValue(Object value) {
    new Visitor().renderValue(value);
  }

  private class Visitor implements ValueConverter<String> {

    public void renderDocument(Object value) {
      if (value instanceof Composite composite) {
        doVisitMap(null, composite.getProperties());
      } else if (value instanceof Map<?, ?> map) {
        doVisitMap(null, map);
      } else if (value instanceof Pair<?, ?> pair) {
        doVisitKeyAndValue(null, pair.getFirst(), pair.getSecond());
      } else {
        throw new RendererException(
            String.format(
                "The top-level value of a Java properties file must have type `Composite`, `Map`, or `Pair`, but got type `%s`.",
                value.getClass().getTypeName()));
      }
    }

    public void renderValue(Object value) {
      write(convert(value), false, restrictCharset);
    }

    @Override
    public String convertNull() {
      return "";
    }

    @Override
    public String convertString(String value) {
      return value;
    }

    @Override
    public String convertInt(Long value) {
      return value.toString();
    }

    @Override
    public String convertFloat(Double value) {
      return value.toString();
    }

    @Override
    public String convertBoolean(Boolean value) {
      return value.toString();
    }

    @Override
    public String convertDuration(Duration value) {
      throw new RendererException(
          String.format(
              "Values of type `Duration` cannot be rendered as Properties. Value: %s", value));
    }

    @Override
    public String convertDataSize(DataSize value) {
      throw new RendererException(
          String.format(
              "Values of type `DataSize` cannot be rendered as Properties. Value: %s", value));
    }

    @Override
    public String convertBytes(byte[] value) {
      throw new RendererException(
          String.format(
              "Values of type `Bytes` cannot be rendered as Properties. Value: %s",
              (Object) value));
    }

    @Override
    public String convertPair(Pair<?, ?> value) {
      throw new RendererException(
          String.format(
              "Values of type `Pair` cannot be rendered as Properties. Value: %s", value));
    }

    @Override
    public String convertList(List<?> value) {
      throw new RendererException(
          String.format(
              "Values of type `List` cannot be rendered as Properties. Value: %s", value));
    }

    @Override
    public String convertSet(Set<?> value) {
      throw new RendererException(
          String.format("Values of type `Set` cannot be rendered as Properties. Value: %s", value));
    }

    @Override
    public String convertMap(Map<?, ?> value) {
      throw new RendererException(
          String.format("Values of type `Map` cannot be rendered as Properties. Value: %s", value));
    }

    @Override
    public String convertObject(PObject value) {
      throw new RendererException(
          String.format(
              "Values of type `Object` cannot be rendered as Properties. Value: %s", value));
    }

    @Override
    public String convertModule(PModule value) {
      throw new RendererException(
          String.format(
              "Values of type `Module` cannot be rendered as Properties. Value: %s", value));
    }

    @Override
    public String convertClass(PClass value) {
      throw new RendererException(
          String.format(
              "Values of type `Class` cannot be rendered as Properties. Value: %s",
              value.getSimpleName()));
    }

    @Override
    public String convertTypeAlias(TypeAlias value) {
      throw new RendererException(
          String.format(
              "Values of type `TypeAlias` cannot be rendered as Properties. Value: %s",
              value.getSimpleName()));
    }

    @Override
    public String convertRegex(Pattern value) {
      throw new RendererException(
          String.format(
              "Values of type `Regex` cannot be rendered as Properties. Value: %s", value));
    }

    private void doVisitMap(@Nullable String keyPrefix, Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        doVisitKeyAndValue(keyPrefix, entry.getKey(), entry.getValue());
      }
    }

    private void doVisitKeyAndValue(@Nullable String keyPrefix, Object key, Object value) {
      if (omitNullProperties && value instanceof PNull) return;

      var keyString = keyPrefix == null ? convert(key) : keyPrefix + "." + convert(key);

      if (value instanceof Composite composite) {
        doVisitMap(keyString, composite.getProperties());
      } else if (value instanceof Map<?, ?> map) {
        doVisitMap(keyString, map);
      } else {
        write(keyString, true, restrictCharset);
        writeSeparator();
        write(convert(value), false, restrictCharset);
        writeLineBreak();
      }
    }

    private void write(String value, boolean escapeSpace, boolean restrictCharset) {
      try {
        writer.write(
            PropertiesUtils.renderPropertiesKeyOrValue(value, escapeSpace, restrictCharset));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    private void writeSeparator() {
      try {
        writer.write(' ');
        writer.write('=');
        writer.write(' ');
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    private void writeLineBreak() {
      try {
        writer.write('\n');
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
