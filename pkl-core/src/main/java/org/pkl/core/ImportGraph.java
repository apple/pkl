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
package org.pkl.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.pkl.core.util.json.Json;
import org.pkl.core.util.json.Json.FormatException;
import org.pkl.core.util.json.Json.JsArray;
import org.pkl.core.util.json.Json.JsObject;
import org.pkl.core.util.json.Json.JsonParseException;
import org.pkl.core.util.json.Json.MappingException;

/**
 * Java representation of {@code pkl.analyze#ImportGraph}.
 *
 * @param imports The graph of imports declared within the program.
 *     <p>Each key is a module inside the program, and each value is the module URIs declared as
 *     imports inside that module. The set of all dependent modules within a program is the set of
 *     keys in this map.
 * @param resolvedImports A mapping of a module's in-language URI, and the URI that it resolves to.
 *     <p>For example, a local package dependency is represented with scheme {@code
 *     projectpackage:}, and (typically) resolves to a {@code file:} scheme.
 */
public record ImportGraph(Map<URI, Set<Import>> imports, Map<URI, URI> resolvedImports) {
  /**
   * Java representation of {@code pkl.analyze#Import}.
   *
   * @param uri The absolute URI of the import.
   */
  public record Import(URI uri) implements Comparable<Import> {
    @Override
    public int compareTo(Import o) {
      return uri.compareTo(o.uri());
    }
  }

  /** Parses the provided JSON into an import graph. */
  public static ImportGraph parseFromJson(String input) throws JsonParseException {
    var parsed = Json.parseObject(input);
    var imports = parseImports(parsed.getObject("imports"));
    var resolvedImports = parseResolvedImports(parsed.getObject("resolvedImports"));
    return new ImportGraph(imports, resolvedImports);
  }

  private static Map<URI, Set<Import>> parseImports(Json.JsObject jsObject)
      throws JsonParseException {
    var ret = new TreeMap<URI, Set<Import>>();
    for (var entry : jsObject.entrySet()) {
      try {
        var key = new URI(entry.getKey());
        var value = entry.getValue();
        var set = new TreeSet<Import>();
        if (!(value instanceof JsArray array)) {
          throw new FormatException("array", value.getClass());
        }
        for (var elem : array) {
          if (!(elem instanceof JsObject importObj)) {
            throw new FormatException("object", elem.getClass());
          }
          set.add(parseImport(importObj));
        }
        ret.put(key, set);
      } catch (URISyntaxException e) {
        throw new MappingException(entry.getKey(), e);
      }
    }
    return ret;
  }

  private static ImportGraph.Import parseImport(Json.JsObject jsObject) throws JsonParseException {
    var uri = jsObject.getURI("uri");
    return new Import(uri);
  }

  private static Map<URI, URI> parseResolvedImports(Json.JsObject jsObject)
      throws JsonParseException {
    var ret = new TreeMap<URI, URI>();
    for (var entry : jsObject.entrySet()) {
      try {
        var key = new URI(entry.getKey());
        var value = entry.getValue();
        if (!(value instanceof String str)) {
          throw new FormatException("string", value.getClass());
        }
        var valueUri = new URI(str);
        ret.put(key, valueUri);
      } catch (URISyntaxException e) {
        throw new MappingException(entry.getKey(), e);
      }
    }
    return ret;
  }
}
