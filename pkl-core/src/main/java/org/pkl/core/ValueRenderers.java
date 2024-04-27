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

import java.io.Writer;

/** Predefined {@link ValueRenderer}s for Pcf, JSON, YAML, and XML property lists. */
public final class ValueRenderers {
  private ValueRenderers() {}

  /**
   * Creates a renderer for Pcf, a static subset of Pkl. If {@code omitNullProperties} is {@code
   * true}, object properties whose value is {@code null} will not be rendered. If {@code
   * useCustomDelimiters} is {@code true}, custom string delimiters (such as {@code #"..."#}) are
   * preferred over escaping quotes and backslashes.
   */
  public static ValueRenderer pcf(
      Writer writer, String indent, boolean omitNullProperties, boolean useCustomStringDelimiters) {
    return new PcfRenderer(writer, indent, omitNullProperties, useCustomStringDelimiters);
  }

  /**
   * Creates a renderer for JSON. If {@code omitNullProperties} is {@code true}, object properties
   * whose value is {@code null} will not be rendered.
   */
  public static ValueRenderer json(Writer writer, String indent, boolean omitNullProperties) {
    return new JsonRenderer(writer, indent, omitNullProperties);
  }

  /**
   * Creates a renderer for YAML. If {@code omitNullProperties} is {@code true}, object properties
   * whose value is {@code null} will not be rendered. If {@code isStream} is {@code true}, {@link
   * ValueRenderer#renderDocument} expects an argument of type {@link Iterable} and renders it as
   * YAML stream.
   */
  public static ValueRenderer yaml(
      Writer writer, int indent, boolean omitNullProperties, boolean isStream) {
    return new YamlRenderer(writer, indent, omitNullProperties, isStream);
  }

  /** Creates a renderer for XML property lists. */
  public static ValueRenderer plist(Writer writer, String indent) {
    return new PListRenderer(writer, indent);
  }

  /**
   * Creates a renderer for {@link java.util.Properties} file format. If {@code omitNullProperties}
   * is {@code true}, object properties and map entries whose value is {@code null} will not be
   * rendered. If {@code restrictCharset} is {@code true} characters outside the printable US-ASCII
   * charset range will be rendered as <a
   * href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-3.html#jls-3.3">Unicode
   * escapes</a>.
   */
  public static ValueRenderer properties(
      Writer writer, boolean omitNullProperties, boolean restrictCharset) {
    return new PropertiesRenderer(writer, omitNullProperties, restrictCharset);
  }
}
