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
package org.pkl.core.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.pkl.core.ImportGraph;

public class ImportGraphUtils {

  private ImportGraphUtils() {}

  /** Find import cycles inside the graph. */
  public static List<List<URI>> findImportCycles(ImportGraph importGraph) {
    var res = new ArrayList<List<URI>>();
    for (var uri : importGraph.imports().keySet()) {
      if (res.stream().anyMatch((it) -> it.contains(uri))) {
        continue;
      }
      var cycle = doFindCycle(uri, importGraph, new ArrayList<>(List.of(uri)));
      if (cycle != null) {
        res.add(cycle);
      }
    }
    return res;
  }

  private static @Nullable List<URI> doFindCycle(
      URI currentUri, ImportGraph importGraph, List<URI> path) {
    var imports = importGraph.imports().get(currentUri);
    var startingUri = path.get(0);
    for (var imprt : imports) {
      var uri = imprt.uri();
      if (uri.equals(startingUri)) {
        return path;
      }
      if (path.contains(uri)) {
        // there is a cycle, but it doesn't start at `startUri`
        return null;
      }
      path.add(uri);
      var cycle = doFindCycle(uri, importGraph, path);
      if (cycle != null) {
        return cycle;
      }
      path.remove(path.size() - 1);
    }
    return null;
  }
}
