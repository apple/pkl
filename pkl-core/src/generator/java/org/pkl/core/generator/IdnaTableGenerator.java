/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.generator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Generates {@code IdnaTableData}, the Unicode data backing {@code pkl:url}'s IDNA/UTS-46
 * implementation.
 *
 * <p>Three tables are emitted, each a contiguous partition of {@code 0..0x10FFFF}:
 *
 * <ul>
 *   <li>the UTS-46 status of each code point, plus the replacement of each mapped range, from
 *       {@code IdnaMappingTable.txt}
 *   <li>whether each code point is a combining mark, from {@code DerivedGeneralCategory.txt}
 *   <li>the Bidi_Class of each code point, from {@code DerivedBidiClass.txt}
 * </ul>
 *
 * <p>General_Category and Bidi_Class are vendored rather than read from {@code Character.getType}
 * and {@code Character.getDirectionality} because those track the runtime JDK's Unicode version,
 * which would make the same Pkl module parse a URL differently on different JVMs.
 */
public final class IdnaTableGenerator {
  private static final String UNICODE_BASE = "https://www.unicode.org/Public";

  private static final int MAX_CODE_POINT = 0x10FFFF;

  /** The number of characters per chunk of an encoded constant. */
  private static final int CHUNK_SIZE = 1000;

  /** The UTS-46 statuses, in encoded-digit order. */
  private static final List<String> STATUSES =
      List.of(
          "valid",
          "ignored",
          "mapped",
          "deviation",
          "disallowed",
          "disallowed_STD3_valid",
          "disallowed_STD3_mapped");

  /** The statuses that carry a replacement, and so contribute to the mapping pool. */
  private static final Set<String> MAPPED_STATUSES = Set.of("mapped", "disallowed_STD3_mapped");

  /** The General_Category values that make a code point a combining mark. */
  private static final Set<String> MARK_CATEGORIES = Set.of("Mn", "Mc", "Me");

  /** The Bidi_Class values RFC 5893 names, in encoded-digit order. */
  private static final List<String> BIDI_CLASSES =
      List.of("L", "R", "AL", "AN", "EN", "ES", "CS", "ET", "ON", "BN", "NSM");

  /** The digit shared by every Bidi_Class value RFC 5893 does not name. */
  private static final int OTHER_BIDI_CLASS = 0xF;

  /** The Bidi_Class values that encode as {@link #OTHER_BIDI_CLASS}. */
  private static final Set<String> OTHER_BIDI_CLASSES =
      Set.of("B", "S", "WS", "LRE", "LRO", "RLE", "RLO", "PDF", "LRI", "RLI", "FSI", "PDI");

  /** Every Bidi_Class long name to its short alias. */
  private static final Map<String, String> BIDI_CLASS_ALIASES =
      Map.ofEntries(
          Map.entry("Left_To_Right", "L"),
          Map.entry("Right_To_Left", "R"),
          Map.entry("Arabic_Letter", "AL"),
          Map.entry("Arabic_Number", "AN"),
          Map.entry("European_Number", "EN"),
          Map.entry("European_Separator", "ES"),
          Map.entry("Common_Separator", "CS"),
          Map.entry("European_Terminator", "ET"),
          Map.entry("Other_Neutral", "ON"),
          Map.entry("Boundary_Neutral", "BN"),
          Map.entry("Nonspacing_Mark", "NSM"),
          Map.entry("Paragraph_Separator", "B"),
          Map.entry("Segment_Separator", "S"),
          Map.entry("White_Space", "WS"),
          Map.entry("Left_To_Right_Embedding", "LRE"),
          Map.entry("Left_To_Right_Override", "LRO"),
          Map.entry("Right_To_Left_Embedding", "RLE"),
          Map.entry("Right_To_Left_Override", "RLO"),
          Map.entry("Pop_Directional_Format", "PDF"),
          Map.entry("Left_To_Right_Isolate", "LRI"),
          Map.entry("Right_To_Left_Isolate", "RLI"),
          Map.entry("First_Strong_Isolate", "FSI"),
          Map.entry("Pop_Directional_Isolate", "PDI"));

  private IdnaTableGenerator() {}

  public static void main(String[] args) {
    if (args.length < 2) {
      throw new IllegalArgumentException(
          "Usage: IdnaTableGenerator <unicode-version> <output-file>");
    }
    var version = args[0];
    var statusTable = readStatusTable(version);
    var markTable = readMarkTable(version);
    var bidiTable = readBidiTable(version);
    write(Path.of(args[1]), render(version, statusTable, markTable, bidiTable));
  }

  /** Reads the UTS-46 status of every code point. */
  private static Table readStatusTable(String version) {
    var uri = URI.create(UNICODE_BASE + "/idna/" + version + "/IdnaMappingTable.txt");
    var entries = new ArrayList<Entry>();
    for (var line : parseDataLines(download(uri))) {
      var value = STATUSES.indexOf(line.value());
      if (value < 0) {
        throw new IllegalArgumentException(
            "Unknown UTS-46 status `" + line.value() + "` at " + name(line.start()) + ".");
      }
      entries.add(new Entry(line.start(), line.end(), value, encodeMapping(line)));
    }
    entries.sort(Comparator.comparingInt(Entry::start));
    requireFullCoverage(entries, uri);
    return encode(merge(entries));
  }

  /**
   * Reads whether each code point is a combining mark. Code points the file does not list are
   * unassigned ({@code Cn}) and so are not marks.
   */
  private static Table readMarkTable(String version) {
    var uri = URI.create(ucdBase(version) + "DerivedGeneralCategory.txt");
    var values = new int[MAX_CODE_POINT + 1];
    for (var line : parseDataLines(download(uri))) {
      if (MARK_CATEGORIES.contains(line.value())) {
        fill(values, line, 1);
      }
    }
    return encode(compress(values));
  }

  /** Reads the Bidi_Class of every code point. */
  private static Table readBidiTable(String version) {
    var uri = URI.create(ucdBase(version) + "DerivedBidiClass.txt");
    var content = download(uri);

    var defaults = parseMissingLines(content);
    if (defaults.isEmpty()
        || defaults.get(0).start() != 0
        || defaults.get(0).end() != MAX_CODE_POINT) {
      throw new IllegalArgumentException(
          "Expected `" + uri + "` to open with a global `@missing` default for U+0000..U+10FFFF.");
    }

    var values = new int[MAX_CODE_POINT + 1];
    // in file order: the global default comes first, and each block-scoped line narrows it
    for (var line : defaults) {
      fill(values, line, bidiValue(line.value()));
    }
    // an explicit entry overrides any default covering it
    for (var line : parseDataLines(content)) {
      fill(values, line, bidiValue(line.value()));
    }
    return encode(compress(values));
  }

  private static String ucdBase(String version) {
    return UNICODE_BASE + "/" + version + "/ucd/extracted/";
  }

  private static int bidiValue(String name) {
    var alias = BIDI_CLASS_ALIASES.getOrDefault(name, name);
    var value = BIDI_CLASSES.indexOf(alias);
    if (value >= 0) {
      return value;
    }
    if (OTHER_BIDI_CLASSES.contains(alias)) {
      return OTHER_BIDI_CLASS;
    }
    throw new IllegalArgumentException("Unknown Bidi_Class `" + name + "`.");
  }

  /**
   * The replacement of a mapped range, as {@code .}-separated hex code points, or {@code null} for
   * a status that carries none.
   */
  private static @Nullable String encodeMapping(DataLine line) {
    if (!MAPPED_STATUSES.contains(line.value())) {
      return null;
    }
    var mapping = line.mapping();
    if (mapping == null || mapping.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected a mapping for the `" + line.value() + "` range at " + name(line.start()) + ".");
    }
    return Arrays.stream(mapping.split(" +"))
        .map(codePoint -> hex(parseCodePoint(codePoint)))
        .collect(Collectors.joining("."));
  }

  /** One {@code start[..end] ; value [; mapping]} line of a data file. */
  private record DataLine(int start, int end, String value, @Nullable String mapping) {}

  private static List<DataLine> parseDataLines(String content) {
    var result = new ArrayList<DataLine>();
    content
        .lines()
        .forEach(
            line -> {
              var comment = line.indexOf('#');
              var body = (comment < 0 ? line : line.substring(0, comment)).trim();
              if (!body.isEmpty()) {
                result.add(parseDataLine(body));
              }
            });
    return result;
  }

  private static List<DataLine> parseMissingLines(String content) {
    var result = new ArrayList<DataLine>();
    content
        .lines()
        .forEach(
            line -> {
              var marker = line.indexOf("@missing:");
              if (marker >= 0) {
                result.add(parseDataLine(line.substring(marker + "@missing:".length()).trim()));
              }
            });
    return result;
  }

  private static DataLine parseDataLine(String body) {
    var fields = body.split(";");
    if (fields.length < 2) {
      throw new IllegalArgumentException(
          "Expected at least two fields in data line `" + body + "`.");
    }
    var range = fields[0].trim();
    var separator = range.indexOf("..");
    var start = parseCodePoint(separator < 0 ? range : range.substring(0, separator));
    var end = separator < 0 ? start : parseCodePoint(range.substring(separator + 2));
    if (end < start) {
      throw new IllegalArgumentException(
          "Expected an ascending range in data line `" + body + "`.");
    }
    return new DataLine(start, end, fields[1].trim(), fields.length > 2 ? fields[2].trim() : null);
  }

  private static int parseCodePoint(String hex) {
    var codePoint = Integer.parseInt(hex, 16);
    if (codePoint < 0 || codePoint > MAX_CODE_POINT) {
      throw new IllegalArgumentException("`" + hex + "` is not a Unicode code point.");
    }
    return codePoint;
  }

  private record Entry(int start, int end, int value, @Nullable String mapping) {}

  /** A contiguous partition of the code point space, encoded. */
  private record Table(String starts, String values, String mappings, int rangeCount) {}

  private static void fill(int[] values, DataLine line, int value) {
    Arrays.fill(values, line.start(), line.end() + 1, value);
  }

  /** Turns a per-code-point array into the ranges it consists of. */
  private static List<Entry> compress(int[] values) {
    var entries = new ArrayList<Entry>();
    var start = 0;
    for (var codePoint = 1; codePoint <= MAX_CODE_POINT; codePoint++) {
      if (values[codePoint] != values[start]) {
        entries.add(new Entry(start, codePoint - 1, values[start], null));
        start = codePoint;
      }
    }
    entries.add(new Entry(start, MAX_CODE_POINT, values[start], null));
    return entries;
  }

  /**
   * Verifies that {@code entries}, sorted by start, partitions {@code 0..0x10FFFF}. A gap would
   * otherwise become a silent wrong answer at a range boundary.
   */
  private static void requireFullCoverage(List<Entry> entries, URI uri) {
    var expected = 0;
    for (var entry : entries) {
      if (entry.start() != expected) {
        var problem =
            entry.start() > expected
                ? "nothing covers " + name(expected)
                : name(entry.start()) + " is covered twice";
        throw new IllegalArgumentException(
            "Expected `" + uri + "` to partition the code point space, but " + problem + ".");
      }
      expected = entry.end() + 1;
    }
    if (expected != MAX_CODE_POINT + 1) {
      throw new IllegalArgumentException(
          "Expected `" + uri + "` to cover U+10FFFF, but it ends at " + name(expected - 1) + ".");
    }
  }

  /** Coalesces adjacent ranges that carry the same value and the same mapping. */
  private static List<Entry> merge(List<Entry> entries) {
    var result = new ArrayList<Entry>();
    for (var entry : entries) {
      var last = result.isEmpty() ? null : result.get(result.size() - 1);
      if (last != null
          && last.value() == entry.value()
          && Objects.equals(last.mapping(), entry.mapping())) {
        result.set(
            result.size() - 1, new Entry(last.start(), entry.end(), last.value(), last.mapping()));
      } else {
        result.add(entry);
      }
    }
    return result;
  }

  private static Table encode(List<Entry> entries) {
    var starts = new StringBuilder();
    var values = new StringBuilder();
    var mappings = new StringBuilder();
    var previousStart = 0;
    for (var entry : entries) {
      if (!starts.isEmpty()) {
        starts.append(',');
      }
      starts.append(Integer.toHexString(entry.start() - previousStart));
      previousStart = entry.start();

      if (entry.value() > 0xF) {
        throw new IllegalArgumentException("Value " + entry.value() + " does not fit in a digit.");
      }
      values.append(Integer.toHexString(entry.value()));

      if (entry.mapping() != null) {
        if (!mappings.isEmpty()) {
          mappings.append(',');
        }
        mappings.append(entry.mapping());
      }
    }
    return new Table(starts.toString(), values.toString(), mappings.toString(), entries.size());
  }

  private static String render(String version, Table status, Table mark, Table bidi) {
    var out = new StringBuilder();
    out.append("package org.pkl.core.stdlib.url;\n");
    out.append("\n");
    out.append("/**\n");
    out.append(" * The Unicode data backing Idna, vendored from Unicode ")
        .append(version)
        .append(".\n");
    out.append(" *\n");
    out.append(" * <p>DO NOT EDIT — generated by {@code IdnaTableGenerator} from Unicode ")
        .append(version)
        .append(".\n");
    out.append(" *\n");
    out.append(" */\n");
    out.append("final class IdnaTableData {\n");
    out.append("  private IdnaTableData() {}\n");
    out.append("\n");
    out.append("  /** The Unicode version every table below was generated from. */\n");
    out.append("  static final String UNICODE_VERSION = \"").append(version).append("\";\n");
    out.append("\n");

    appendConstant(
        out, "STATUS_STARTS", status.rangeCount() + " UTS-46 status ranges.", status.starts());
    appendConstant(out, "STATUS_VALUES", "The status of each range.", status.values());
    appendConstant(
        out,
        "STATUS_MAPPINGS",
        "The replacement of each mapped range, in range order.",
        status.mappings());
    appendConstant(
        out, "MARK_STARTS", mark.rangeCount() + " combining-mark ranges.", mark.starts());
    appendConstant(out, "MARK_VALUES", "Whether each range is a combining mark.", mark.values());
    appendConstant(out, "BIDI_STARTS", bidi.rangeCount() + " Bidi_Class ranges.", bidi.starts());
    appendConstant(out, "BIDI_VALUES", "The Bidi_Class of each range.", bidi.values());

    out.append("}\n");
    return out.toString();
  }

  private static void appendConstant(StringBuilder out, String name, String doc, String value) {
    out.append("  /** ").append(doc).append(" */\n");
    out.append("  static final String[] ").append(name).append(" = {\n");
    for (var i = 0; i < value.length(); i += CHUNK_SIZE) {
      out.append("    \"")
          .append(value, i, Math.min(i + CHUNK_SIZE, value.length()))
          .append("\",\n");
    }
    out.append("  };\n");
    out.append("\n");
  }

  /** A code point as the lowercase hex the encoded tables use. */
  private static String hex(int codePoint) {
    return Integer.toHexString(codePoint);
  }

  private static String name(int codePoint) {
    return String.format("U+%04X", codePoint);
  }

  private static String download(URI uri) {
    try (var in = uri.toURL().openStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to download `" + uri + "`.", e);
    }
  }

  private static void write(Path outputFile, String content) {
    try {
      var parent = outputFile.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(outputFile, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
