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
package org.pkl.core.project

import java.net.URI
import java.nio.file.Path
import java.util.regex.Pattern
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.PackageServer
import org.pkl.commons.toPath
import org.pkl.commons.writeString
import org.pkl.core.*
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings
import org.pkl.core.http.HttpClient
import org.pkl.core.packages.PackageUri

class ProjectTest {
  @Test
  fun loadFromPath(@TempDir path: Path) {
    val projectPath = path.resolve("PklProject")
    val expectedPackage =
      Package(
        "hawk",
        PackageUri("package://example.com/hawk@0.5.0"),
        Version.parse("0.5.0"),
        URI("https://example.com/hawk/0.5.0/hawk-0.5.0.zip"),
        "Some project about hawks",
        listOf("Birdy Bird <birdy@bird.com>"),
        URI("https://example.com/my/website"),
        URI("https://example.com/my/docs"),
        URI("https://example.com/my/repo"),
        "https://example.com/my/repo/0.5.0%{path}",
        "MIT",
        """
        # Some License text
        
        This is my license text
      """
          .trimIndent(),
        URI("https://example.com/my/issues"),
        listOf(Path.of("apiTest1.pkl"), Path.of("apiTest2.pkl")),
        listOf("PklProject", "PklProject.deps.json", ".**", "*.exe")
      )
    val expectedSettings =
      PklEvaluatorSettings(
        mapOf("two" to "2"),
        mapOf("one" to "1"),
        listOf("foo:", "bar:").map(Pattern::compile),
        listOf("baz:", "biz:").map(Pattern::compile),
        false,
        path.resolve("cache/"),
        listOf(path.resolve("modulepath1/"), path.resolve("modulepath2/")),
        Duration.ofMinutes(5.0),
        path,
        null
      )
    projectPath.writeString(
      """
      amends "pkl:Project"

      evaluatorSettings {
        timeout = 5.min
        rootDir = "."
        noCache = false
        moduleCacheDir = "cache/"
        env {
          ["one"] = "1"
        }
        externalProperties {
          ["two"] = "2"
        }
        modulePath {
          "modulepath1/"
          "modulepath2/"
        }
        allowedModules {
          "foo:"
          "bar:"
        }
        allowedResources {
          "baz:"
          "biz:"
        }
      }

      package {
        name = "hawk"
        baseUri = "package://example.com/hawk"
        version = "0.5.0"
        description = "Some project about hawks"
        packageZipUrl = "https://example.com/hawk/\(version)/hawk-\(version).zip"
        authors {
          "Birdy Bird <birdy@bird.com>"
        }
        license = "MIT"
        sourceCode = "https://example.com/my/repo"
        sourceCodeUrlScheme = "https://example.com/my/repo/\(version)%{path}"
        documentation = "https://example.com/my/docs"
        website = "https://example.com/my/website"
        licenseText = ""${'"'}
          # Some License text
          
          This is my license text
          ""${'"'}
        apiTests {
          "apiTest1.pkl"
          "apiTest2.pkl"
        }
        exclude { "*.exe" }
        issueTracker = "https://example.com/my/issues"
      }
      
      tests {
        "test1.pkl"
        "test2.pkl"
      }
    """
        .trimIndent()
    )
    val project = Project.loadFromPath(projectPath)
    assertThat(project.`package`).isEqualTo(expectedPackage)
    assertThat(project.evaluatorSettings).isEqualTo(expectedSettings)
    assertThat(project.tests)
      .isEqualTo(listOf(path.resolve("test1.pkl"), path.resolve("test2.pkl")))
  }

  @Test
  fun `load wrong type`(@TempDir path: Path) {
    val projectPath = path.resolve("PklProject")
    projectPath.writeString(
      """
      module com.apple.Foo

      foo = 1
    """
        .trimIndent()
    )
    assertThatCode { Project.loadFromPath(projectPath, SecurityManagers.defaultManager, null) }
      .hasMessageContaining("be of type `pkl.Project`, but got type `com.apple.Foo`")
  }

  @Test
  fun `evaluate project module -- invalid checksum`() {
    PackageServer().use { server ->
      val projectDir = Path.of(javaClass.getResource("badProjectChecksum2/")!!.toURI())
      val project = Project.loadFromPath(projectDir.resolve("PklProject"))
      val httpClient =
        HttpClient.builder()
          .addCertificates(FileTestUtils.selfSignedCertificate)
          .setTestPort(server.port)
          .build()
      val evaluator =
        EvaluatorBuilder.preconfigured()
          .applyFromProject(project)
          .setModuleCacheDir(null)
          .setHttpClient(httpClient)
          .build()
      assertThatCode { evaluator.evaluate(ModuleSource.path(projectDir.resolve("bug.pkl"))) }
        .hasMessageStartingWith(
          """
        –– Pkl Error ––
        Cannot download package `package://localhost:0/fruit@1.0.5` because the computed checksum for package metadata does not match the expected checksum.
        
        Computed checksum: "${PackageServer.FRUIT_SHA}"
        Expected checksum: "intentionally bogus checksum"
        Asset URL: "https://localhost:0/fruit@1.0.5"
        
        1 | import "@fruit/Fruit.pkl"
            ^^^^^^^^^^^^^^^^^^^^^^^^^
      """
            .trimIndent()
        )
    }
  }

  @Test
  fun `fails if project has cyclical dependencies`() {
    val projectPath = javaClass.getResource("projectCycle1/PklProject")!!.toURI().toPath()
    val e = assertThrows<PklException> { Project.loadFromPath(projectPath) }
    val cleanMsg = e.message!!.replace(Regex(".*/resources/test"), "file://")
    assertThat(cleanMsg)
      .isEqualTo(
        """
        Local project dependencies cannot be circular.
  
        Cycle:
        ┌─>
        file:///org/pkl/core/project/projectCycle2/PklProject
        │
        file:///org/pkl/core/project/projectCycle3/PklProject
        └─ 
        """
          .trimIndent()
      )
  }
}
