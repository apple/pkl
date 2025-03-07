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
package org.pkl.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.*;
import java.util.regex.Pattern;
import org.pkl.core.util.ArrayCharEscaper;
import org.pkl.core.util.ByteArrayUtils;

// To instantiate this class, use ValueRenderers.plist().
final class PListRenderer implements ValueRenderer {
  private static final String LINE_BREAK = "\n";

  // it's safe (though not required) to escape all of the following characters in XML text nodes
  private static final ArrayCharEscaper charEscaper =
      ArrayCharEscaper.builder()
          .withEscape('"', "&quot;")
          .withEscape('\'', "&apos;")
          .withEscape('<', "&lt;")
          .withEscape('>', "&gt;")
          .withEscape('&', "&amp;")
          .build();

  private final Writer writer;
  private final String indent;

  public PListRenderer(Writer writer, String indent) {
    this.writer = writer;
    this.indent = indent;
  }

  @Override
  public void renderDocument(Object value) {
    if (!(value instanceof Collection || value instanceof Map || value instanceof Composite)) {
      // as far as I can tell, only arrays and dicts are allowed as top-level values
      // see:
      // https://github.com/apple/swift/blob/2bf6b88585ba0bc756c8a50c95081fc08f47fbf0/stdlib/public/SDK/Foundation/PlistEncoder.swift#L79
      throw new RendererException(
          String.format(
              "The top-level value of an XML property list must have type `Collection`, `Map`, or `Composite`, but got type `%s`.",
              value.getClass().getTypeName()));
    }

    write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    write(LINE_BREAK);
    write(
        "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">");
    write(LINE_BREAK);
    write("<plist version=\"1.0\">");
    write(LINE_BREAK);

    new Visitor().visit(value);

    write(LINE_BREAK);
    write("</plist>");
    write(LINE_BREAK);
  }

  @Override
  public void renderValue(Object value) {
    new Visitor().visit(value);
  }

  // keep in sync with org.pkl.core.stdlib.PListRendererNodes.PListRenderer
  private class Visitor implements ValueVisitor {
    private String currIndent = "";

    @Override
    public void visitString(String value) {
      write("<string>");
      write(charEscaper.escape(value));
      write("</string>");
    }

    @Override
    public void visitInt(Long value) {
      write("<integer>");
      write(value.toString());
      write("</integer>");
    }

    // according to:
    // https://www.apple.com/DTDs/PropertyList-1.0.dtd
    // http://www.atomicbird.com/blog/json-vs-plists (nan, infinity)
    @Override
    @SuppressWarnings("Duplicates")
    public void visitFloat(Double value) {
      write("<real>");

      if (value.isNaN()) {
        write("nan");
      } else if (value == Double.POSITIVE_INFINITY) {
        write("+infinity");
      } else if (value == Double.NEGATIVE_INFINITY) {
        write("-infinity");
      } else {
        write(value.toString());
      }

      write("</real>");
    }

    @Override
    public void visitBoolean(Boolean value) {
      write(value ? "<true/>" : "<false/>");
    }

    @Override
    public void visitDuration(Duration value) {
      throw new RendererException(
          String.format(
              "Values of type `Duration` cannot be rendered as XML property list. Value: %s",
              value));
    }

    @Override
    public void visitDataSize(DataSize value) {
      throw new RendererException(
          String.format(
              "Values of type `DataSize` cannot be rendered as XML property list. Value: %s",
              value));
    }

    @Override
    public void visitBytes(byte[] value) {
      write("<data>");
      write(ByteArrayUtils.base64(value));
      write("</data>");
    }

    @Override
    public void visitPair(Pair<?, ?> value) {
      doVisitIterable(value, false);
    }

    @Override
    public void visitList(List<?> value) {
      doVisitIterable(value, value.isEmpty());
    }

    @Override
    public void visitSet(Set<?> value) {
      doVisitIterable(value, value.isEmpty());
    }

    @Override
    public void visitMap(Map<?, ?> map) {
      var renderedAtLeastOneEntry = false;

      for (var entry : map.entrySet()) {
        var key = entry.getKey();
        if (!(key instanceof String)) {
          throw new RendererException(
              String.format(
                  "Maps with non-String keys cannot be rendered as XML property list. Key: %s",
                  key));
        }

        var value = entry.getValue();
        if (value instanceof PNull) continue;

        if (!renderedAtLeastOneEntry) {
          write("<dict>");
          write(LINE_BREAK);
          currIndent += indent;
          renderedAtLeastOneEntry = true;
        }

        write(currIndent);
        write("<key>");
        write(charEscaper.escape((String) key));
        write("</key>");
        write(LINE_BREAK);

        write(currIndent);
        visit(value);
        write(LINE_BREAK);
      }

      if (renderedAtLeastOneEntry) {
        currIndent = currIndent.substring(0, currIndent.length() - indent.length());
        write(currIndent);
        write("</dict>");
      } else {
        write("<dict/>");
      }
    }

    @Override
    public void visitObject(PObject value) {
      doVisitComposite(value);
    }

    @Override
    public void visitModule(PModule value) {
      doVisitComposite(value);
    }

    @Override
    public void visitNull() {
      throw new RendererException("`null` values cannot be rendered as XML property list.");
    }

    @Override
    public void visitClass(PClass value) {
      throw new RendererException(
          String.format(
              "Values of type `Class` cannot be rendered as XML property list. Value: %s", value));
    }

    @Override
    public void visitTypeAlias(TypeAlias value) {
      throw new RendererException(
          String.format(
              "Values of type `TypeAlias` cannot be rendered as XML property list. Value: %s",
              value.getSimpleName()));
    }

    @Override
    public void visitRegex(Pattern value) {
      throw new RendererException(
          String.format(
              "Values of type `Regex` cannot be rendered as XML property list. Value: %s", value));
    }

    private void doVisitIterable(Iterable<?> iterable, boolean isEmpty) {
      if (isEmpty) {
        write("<array/>");
        return;
      }

      write("<array>");
      write(LINE_BREAK);
      currIndent += indent;

      for (var elem : iterable) {
        write(currIndent);
        visit(elem);
        write(LINE_BREAK);
      }

      currIndent = currIndent.substring(0, currIndent.length() - indent.length());
      write(currIndent);
      write("</array>");
    }

    private void doVisitComposite(Composite composite) {
      var renderedAtLeastOneProperty = false;

      for (var entry : composite.getProperties().entrySet()) {
        var value = entry.getValue();
        if (value instanceof PNull) continue;

        if (!renderedAtLeastOneProperty) {
          write("<dict>");
          write(LINE_BREAK);
          currIndent += indent;
          renderedAtLeastOneProperty = true;
        }

        write(currIndent);
        write("<key>");
        write(charEscaper.escape(entry.getKey()));
        write("</key>");
        write(LINE_BREAK);

        write(currIndent);
        visit(value);
        write(LINE_BREAK);
      }

      if (renderedAtLeastOneProperty) {
        currIndent = currIndent.substring(0, currIndent.length() - indent.length());
        write(currIndent);
        write("</dict>");
      } else {
        write("<dict/>");
      }
    }
  }

  private void write(String str) {
    try {
      writer.write(str);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
