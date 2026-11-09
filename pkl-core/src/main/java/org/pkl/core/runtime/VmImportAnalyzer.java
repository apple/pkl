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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.NoSuchFileException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.pkl.core.ImportGraph;
import org.pkl.core.ImportGraph.Import;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.ast.builder.ImportsAndReadsParser;
import org.pkl.core.ast.builder.ImportsAndReadsParser.Entry;
import org.pkl.core.externalreader.ExternalReaderProcessException;
import org.pkl.core.module.ResolvedModuleKey;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.util.GlobResolver;
import org.pkl.core.util.GlobResolver.InvalidGlobPatternException;
import org.pkl.core.util.IoUtils;

public class VmImportAnalyzer {
  @TruffleBoundary
  public static ImportGraph analyze(URI[] moduleUris, VmContext context)
      throws IOException, SecurityManagerException, ExternalReaderProcessException {
    var imports = new TreeMap<URI, Set<ImportGraph.Import>>();
    var resolvedImports = new TreeMap<URI, URI>();
    for (var moduleUri : moduleUris) {
      var moduleKey = context.getModuleResolver().resolve(moduleUri);
      var resolvedModuleKey = moduleKey.resolve(context.getSecurityManager());
      analyzeSingle(moduleUri, resolvedModuleKey, context, imports, resolvedImports);
    }
    return new ImportGraph(imports, resolvedImports);
  }

  @TruffleBoundary
  private static void analyzeSingle(
      URI moduleUri,
      ResolvedModuleKey resolvedModuleKey,
      VmContext context,
      Map<URI, Set<ImportGraph.Import>> imports,
      Map<URI, URI> resolvedImports) {
    var moduleResolver = context.getModuleResolver();
    var securityManager = context.getSecurityManager();
    var collectedImports = collectImports(resolvedModuleKey, moduleResolver, securityManager);
    var importsInModule = new TreeSet<Import>();
    for (var imprt : collectedImports) {
      importsInModule.add(imprt.toImport());
    }
    imports.put(moduleUri, importsInModule);
    resolvedImports.put(moduleUri, resolvedModuleKey.getUri());
    for (var imprt : collectedImports) {
      if (imports.containsKey(imprt.moduleUri)) {
        continue;
      }
      analyzeSingle(imprt.moduleUri, imprt.resolvedModuleKey, context, imports, resolvedImports);
    }
  }

  private static Set<ImportEntry> collectImports(
      ResolvedModuleKey resolvedModuleKey,
      ModuleResolver moduleResolver,
      SecurityManager securityManager) {
    List<Entry> importsAndReads;
    var moduleKey = resolvedModuleKey.getOriginal();
    try {
      importsAndReads = ImportsAndReadsParser.parse(moduleKey, resolvedModuleKey);
    } catch (VmException err) {
      throw new VmExceptionBuilder()
          .evalError("cannotAnalyzeBecauseSyntaxError", moduleKey.getUri())
          .wrapping(err)
          .build();
    } catch (IOException err) {
      throw new VmExceptionBuilder().evalError("ioErrorLoadingModule", moduleKey.getUri()).build();
    }
    var result = new HashSet<ImportEntry>();
    for (var entry : importsAndReads) {
      if (!entry.isModule()) {
        continue;
      }
      try {
        if (entry.isGlob()) {
          var theModuleKey =
              moduleResolver.resolve(moduleKey.resolveUri(IoUtils.toUri(entry.stringValue())));
          var elements =
              GlobResolver.resolveGlob(
                  securityManager,
                  theModuleKey,
                  moduleKey,
                  moduleKey.getUri(),
                  entry.stringValue());
          for (var globElement : elements.values()) {
            var moduleUri = globElement.uri();
            var mk = moduleResolver.resolve(moduleUri);
            var rmk = mk.resolve(securityManager);
            result.add(new ImportEntry(moduleUri, rmk));
          }
        } else {
          var resolvedUri =
              IoUtils.resolve(securityManager, moduleKey, IoUtils.toUri(entry.stringValue()));
          var mKey = moduleResolver.resolve(resolvedUri);
          var rmk = mKey.resolve(securityManager);
          result.add(new ImportEntry(resolvedUri, rmk));
        }
      } catch (InvalidGlobPatternException e) {
        throw new VmExceptionBuilder()
            .evalError("invalidGlobPattern", entry.stringValue())
            .withSourceSection(entry.sourceSection())
            .build();
      } catch (FileNotFoundException | NoSuchFileException e) {
        throw new VmExceptionBuilder()
            .evalError("cannotFindModule", entry.stringValue())
            .withSourceSection(entry.sourceSection())
            .build();
      } catch (URISyntaxException e) {
        throw new VmExceptionBuilder()
            .evalError("invalidModuleUri", entry.stringValue())
            .withHint(e.getReason())
            .withSourceSection(entry.sourceSection())
            .build();
      } catch (IOException e) {
        throw new VmExceptionBuilder()
            .evalError("ioErrorLoadingModule", entry.stringValue())
            .withCause(e)
            .withSourceSection(entry.sourceSection())
            .build();
      } catch (SecurityManagerException | PackageLoadError e) {
        throw new VmExceptionBuilder()
            .withSourceSection(entry.sourceSection())
            .withCause(e)
            .build();
      } catch (ExternalReaderProcessException e) {
        throw new VmExceptionBuilder()
            .withSourceSection(entry.sourceSection())
            .evalError("externalReaderFailure")
            .withCause(e)
            .build();
      }
    }
    return result;
  }

  private record ImportEntry(URI moduleUri, ResolvedModuleKey resolvedModuleKey) {
    private Import toImport() {
      return new Import(resolvedModuleKey.getOriginal().getUri());
    }
  }
}
