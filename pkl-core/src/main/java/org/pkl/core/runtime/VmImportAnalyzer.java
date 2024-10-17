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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.ast.builder.ImportsAndReadsParser;
import org.pkl.core.ast.builder.ImportsAndReadsParser.Entry;
import org.pkl.core.util.GlobResolver;
import org.pkl.core.util.GlobResolver.InvalidGlobPatternException;
import org.pkl.core.util.GlobResolver.ResolvedGlobElement;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Pair;

public class VmImportAnalyzer {
  @TruffleBoundary
  public static Pair<Map<URI, Set<URI>>, Map<URI, URI>> analyze(URI[] moduleUris, VmContext context)
      throws IOException, URISyntaxException, SecurityManagerException {
    var imports = new TreeMap<URI, Set<URI>>();
    var resolvedImports = new TreeMap<URI, URI>();
    for (var moduleUri : moduleUris) {
      analyzeSingle(moduleUri, context, imports, resolvedImports);
    }
    return Pair.of(imports, resolvedImports);
  }

  @TruffleBoundary
  private static void analyzeSingle(
      URI moduleUri, VmContext context, Map<URI, Set<URI>> imports, Map<URI, URI> resolvedImports)
      throws IOException, URISyntaxException, SecurityManagerException {
    var moduleResolver = context.getModuleResolver();
    var securityManager = context.getSecurityManager();
    var importsInModule = collectImports(moduleUri, moduleResolver, securityManager);

    imports.put(moduleUri, importsInModule);
    resolvedImports.put(
        moduleUri, moduleResolver.resolve(moduleUri).resolve(securityManager).getUri());
    for (var importUri : importsInModule) {
      if (imports.containsKey(importUri)) {
        continue;
      }
      analyzeSingle(importUri, context, imports, resolvedImports);
    }
  }

  private static Set<URI> collectImports(
      URI moduleUri, ModuleResolver moduleResolver, SecurityManager securityManager)
      throws IOException, URISyntaxException, SecurityManagerException {
    var moduleKey = moduleResolver.resolve(moduleUri);
    var resolvedModuleKey = moduleKey.resolve(securityManager);
    List<Entry> importsAndReads;
    try {
      importsAndReads = ImportsAndReadsParser.parse(moduleKey, resolvedModuleKey);
    } catch (VmException err) {
      throw new VmExceptionBuilder()
          .evalError("cannotAnalyzeBecauseSyntaxError", moduleKey.getUri())
          .wrapping(err)
          .build();
    }
    if (importsAndReads == null) {
      return Set.of();
    }
    var result = new TreeSet<URI>();
    for (var entry : importsAndReads) {
      if (!entry.isModule()) {
        continue;
      }
      if (entry.isGlob()) {
        var theModuleKey =
            moduleResolver.resolve(moduleKey.resolveUri(IoUtils.toUri(entry.stringValue())));
        try {
          var elements =
              GlobResolver.resolveGlob(
                  securityManager,
                  theModuleKey,
                  moduleKey,
                  moduleKey.getUri(),
                  entry.stringValue());
          result.addAll(elements.values().stream().map(ResolvedGlobElement::getUri).toList());
        } catch (InvalidGlobPatternException e) {
          throw new VmExceptionBuilder()
              .evalError("invalidGlobPattern", entry.stringValue())
              .withSourceSection(entry.sourceSection())
              .build();
        }
      } else {
        var resolvedUri =
            IoUtils.resolve(securityManager, moduleKey, IoUtils.toUri(entry.stringValue()));
        result.add(resolvedUri);
      }
    }
    return result;
  }
}
