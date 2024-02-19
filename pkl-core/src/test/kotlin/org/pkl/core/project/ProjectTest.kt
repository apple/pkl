package org.pkl.core.project

import org.pkl.commons.test.PackageServer
import org.pkl.commons.writeString
import org.pkl.core.*
import org.pkl.core.packages.PackageUri
import org.pkl.core.project.Project.EvaluatorSettings
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.test.Ignore
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class ProjectTest {
  @Test
  fun loadFromPath(@TempDir path: Path) {
    val projectPath = path.resolve("PklProject")
    val expectedPackage = Package(
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
      """.trimIndent(),
      URI("https://example.com/my/issues"),
      listOf(Path.of("apiTest1.pkl"), Path.of("apiTest2.pkl")),
      listOf("PklProject", "PklProject.deps.json", ".**", "*.exe")
    )
    val expectedSettings = EvaluatorSettings(
      mapOf("two" to "2"),
      mapOf("one" to "1"),
      listOf("foo:", "bar:").map(Pattern::compile),
      listOf("baz:", "biz:").map(Pattern::compile),
      false,
      path.resolve("cache/"),
      listOf(
        path.resolve("modulepath1/"),
        path.resolve("modulepath2/")
      ),
      Duration.ofMinutes(5.0),
      path
    )
    projectPath.writeString("""
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
    """.trimIndent())
    val project = Project.loadFromPath(projectPath)
    assertThat(project.`package`).isEqualTo(expectedPackage)
    assertThat(project.settings).isEqualTo(expectedSettings)
    assertThat(project.tests).isEqualTo(listOf(path.resolve("test1.pkl"), path.resolve("test2.pkl")))
  }

  @Test
  fun `load wrong type`(@TempDir path: Path) {
    val projectPath = path.resolve("PklProject")
    projectPath.writeString("""
      module com.apple.Foo

      foo = 1
    """.trimIndent())
    assertThatCode {
      Project.loadFromPath(projectPath, SecurityManagers.defaultManager, 5.seconds.toJavaDuration())
    }
      .hasMessageContaining("be of type `pkl.Project`, but got type `com.apple.Foo`")
  }

  @Test @Ignore("sgammon: Broken checksums")
  fun `evaluate project module -- invalid checksum`() {
    PackageServer.ensureStarted()
    val projectDir = Path.of(javaClass.getResource("badProjectChecksum2/")!!.path)
    val project = Project.loadFromPath(projectDir.resolve("PklProject"))
    val evaluator = EvaluatorBuilder.preconfigured()
      .applyFromProject(project)
      .setModuleCacheDir(null)
      .build()
    assertThatCode { evaluator.evaluate(ModuleSource.path(projectDir.resolve("bug.pkl"))) }
      .hasMessageStartingWith("""
        –– Pkl Error ––
        Cannot download package `package://localhost:12110/fruit@1.0.5` because the computed checksum for package metadata does not match the expected checksum.
        
        Computed checksum: "b4ea243de781feeab7921227591e6584db5d0673340f30fab2ffe8ad5c9f75f5"
        Expected checksum: "intentionally bogus checksum"
        Asset URL: "https://localhost:12110/fruit@1.0.5"
        
        1 | import "@fruit/Fruit.pkl"
            ^^^^^^^^^^^^^^^^^^^^^^^^^
      """.trimIndent())
  }
}
