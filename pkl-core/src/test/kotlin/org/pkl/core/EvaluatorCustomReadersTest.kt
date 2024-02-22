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
package org.pkl.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.test.PackageServer
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.project.Project
import org.pkl.core.resource.ResourceReaders
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class EvaluatorCustomReadersTest {
  @Test
  fun `evaluate with project dependencies from custom URI`(@TempDir tempDir: Path, @TempDir cacheDir: Path) {
    PackageServer.populateCacheDir(cacheDir)
    val libDir = tempDir.resolve("lib/").createDirectories()
    libDir
      .resolve("lib.pkl")
      .writeText(
        """
      text = "This is from lib"
    """
          .trimIndent()
      )
    libDir
      .resolve("PklProject")
      .writeText(
        """
      amends "pkl:Project"
      
      package {
        name = "lib"
        baseUri = "package://localhost:12110/lib"
        version = "5.0.0"
        packageZipUrl = "https://localhost:12110/lib.zip"
      }
    """
          .trimIndent()
      )
    val projectDir = tempDir.resolve("proj/").createDirectories()
    val module = projectDir.resolve("mod.pkl")
    module.writeText(
      """
      import "@birds/Bird.pkl"
      import "@lib/lib.pkl"
      
      res: Bird = new {
        name = "Birdie"
        favoriteFruit { name = "dragonfruit" }
      }
      
      libContents = lib
    """
        .trimIndent()
    )
    projectDir
      .resolve("PklProject")
      .writeText(
        """
      amends "pkl:Project"
      
      dependencies {
        ["birds"] {
          uri = "package://localhost:12110/birds@0.5.0"
        }
        ["lib"] = import("../lib/PklProject")
      }
    """
          .trimIndent()
      )
    val dollar = '$'
    projectDir
      .resolve("PklProject.deps.json")
      .writeText(
        """
      {
        "schemaVersion": 1,
        "resolvedDependencies": {
          "package://localhost:12110/birds@0": {
            "type": "remote",
            "uri": "projectpackage://localhost:12110/birds@0.5.0",
            "checksums": {
              "sha256": "${dollar}skipChecksumVerification"
            }
          },
          "package://localhost:12110/fruit@1": {
            "type": "remote",
            "uri": "projectpackage://localhost:12110/fruit@1.0.5",
            "checksums": {
              "sha256": "${dollar}skipChecksumVerification"
            }
          },
          "package://localhost:12110/lib@5": {
            "type": "local",
            "uri": "projectpackage://localhost:12110/lib@5.0.0",
            "path": "../lib"
          }
        }
      }

    """
          .trimIndent()
      )

    val evalBase = EvaluatorBuilder.unconfigured()
      .setStackFrameTransformer(StackFrameTransformers.defaultTransformer)
      .setAllowedModules(listOf(Pattern.compile("custom:"), Pattern.compile("pkl:"), Pattern.compile("projectpackage:"), Pattern.compile("package:")))
      .setAllowedResources(listOf(Pattern.compile("custom:"), Pattern.compile("prop:")))
      .addModuleKeyFactory(ModuleKeyFactories.standardLibrary)
      .addModuleKeyFactory(ModuleKeyFactories.projectpackage)
      .addModuleKeyFactory(ModuleKeyFactories.pkg)
      .addModuleKeyFactory(EvaluatorCustomReaders.CustomModuleKeyFactory("custom", tempDir))
      .addResourceReader(ResourceReaders.externalProperty())
      .addResourceReader(EvaluatorCustomReaders.CustomResourceReader("custom", tempDir))

    
    val projEval = evalBase.build()
    val projOutput = projEval.evaluateOutputValueAs(ModuleSource.uri("custom:///proj/PklProject"), PClassInfo.Project)
    val proj = Project.parseProject(projOutput)
    
    val eval = evalBase.setProjectDependencies(proj.dependencies).build()
    val output = eval.evaluateExpressionString(ModuleSource.uri("custom:///proj/mod.pkl"), "output.text")
    assertThat(output)
      .isEqualTo(
        """
        res {
          name = "Birdie"
          favoriteFruit {
            name = "dragonfruit"
          }
        }
        libContents {
          text = "This is from lib"
        }
        
        """
          .trimIndent()
      )

  }

}
