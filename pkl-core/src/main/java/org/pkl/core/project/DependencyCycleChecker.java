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
package org.pkl.core.project;

import java.util.Map;
import java.util.stream.Collectors;
import org.pkl.core.EvaluatorBuilder;
import org.pkl.core.ModuleSource;
import org.pkl.core.PObject;
import org.pkl.core.PklException;
import org.pkl.core.module.ModuleKeyFactories;
import org.pkl.core.util.ErrorMessages;

public class DependencyCycleChecker {

  private final ModuleSource module;

  public DependencyCycleChecker(ModuleSource module) {
    this.module = module;
  }

  private final ModuleSource sourceModule =
      ModuleSource.text(
          """
          import "pkl:analyze"

          local importStrings = Set(read("prop:pkl.analyzeImports"))

          output {
            value = analyze.importGraph(importStrings)
            renderer {
              converters {
                [Map] = (it) -> it.toMapping()
                [Set] = (it) -> it.toListing()
              }
            }
          }
          """);

  @SuppressWarnings("unchecked")
  public void checkCycles() {
    PObject importGraph;
    try {
      importGraph = render();
    } catch (Exception e) {
      // just ignore the error and report the original exception
      return;
    }
    var resolvedImports = (Map<String, String>) importGraph.getProperty("resolvedImports");

    var possibleCycle =
        resolvedImports.values().stream()
            .filter((_import) -> _import.startsWith("file:"))
            .collect(Collectors.joining("\n  "));

    throw new PklException(ErrorMessages.create("dependencyCycle", "  " + possibleCycle));
  }

  private PObject render() {
    var builder = EvaluatorBuilder.preconfigured();
    try {
      builder.addExternalProperty("pkl.analyzeImports", module.getUri().toString());
      try (var evaluator = builder.build()) {
        return (PObject) evaluator.evaluateOutputValue(sourceModule);
      }
    } finally {
      ModuleKeyFactories.closeQuietly(builder.getModuleKeyFactories());
    }
  }
}
