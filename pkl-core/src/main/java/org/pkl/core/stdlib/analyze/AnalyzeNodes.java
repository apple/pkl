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
package org.pkl.core.stdlib.analyze;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.pkl.core.ImportGraph;
import org.pkl.core.ImportGraph.Import;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.externalreader.ReaderProcessException;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.runtime.AnalyzeModule;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmImportAnalyzer;
import org.pkl.core.runtime.VmMap;
import org.pkl.core.runtime.VmSet;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.VmObjectFactory;

public final class AnalyzeNodes {
  private AnalyzeNodes() {}

  private static VmObjectFactory<Import> importFactory =
      new VmObjectFactory<Import>(AnalyzeModule::getImportClass)
          .addStringProperty("uri", (it) -> it.uri().toString());

  private static VmObjectFactory<ImportGraph> importGraphFactory =
      new VmObjectFactory<ImportGraph>(AnalyzeModule::getImportGraphClass)
          .addMapProperty(
              "imports",
              graph -> {
                var builder = VmMap.builder();
                for (var entry : graph.imports().entrySet()) {
                  var vmSetBuilder = VmSet.EMPTY.builder();
                  for (var imprt : entry.getValue()) {
                    vmSetBuilder.add(importFactory.create(imprt));
                  }
                  builder.add(entry.getKey().toString(), vmSetBuilder.build());
                }
                return builder.build();
              })
          .addMapProperty(
              "resolvedImports",
              graph -> {
                var builder = VmMap.builder();
                for (var entry : graph.resolvedImports().entrySet()) {
                  builder.add(entry.getKey().toString(), entry.getValue().toString());
                }
                return builder.build();
              });

  public abstract static class importGraph extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(@SuppressWarnings("unused") VmTyped self, VmSet moduleUris) {
      var uris = new URI[moduleUris.getLength()];
      var idx = 0;
      for (var moduleUri : moduleUris) {
        URI uri;
        try {
          uri = new URI((String) moduleUri);
        } catch (URISyntaxException e) {
          throw exceptionBuilder()
              .evalError("invalidModuleUri", moduleUri)
              .withHint(e.getMessage())
              .build();
        }
        if (!uri.isAbsolute()) {
          throw exceptionBuilder().evalError("cannotAnalyzeRelativeModuleUri", moduleUri).build();
        }
        uris[idx] = uri;
        idx++;
      }
      var context = VmContext.get(this);
      try {
        var results = VmImportAnalyzer.analyze(uris, context);
        return importGraphFactory.create(results);
      } catch (IOException
          | SecurityManagerException
          | PackageLoadError
          | ReaderProcessException e) {
        throw exceptionBuilder().withCause(e).build();
      }
    }
  }
}
