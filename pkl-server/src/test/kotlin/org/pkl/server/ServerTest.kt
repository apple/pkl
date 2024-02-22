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
package org.pkl.server

import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.msgpack.core.MessagePack
import org.pkl.commons.test.PackageServer
import org.pkl.core.module.PathElement

@Disabled("sgammon: Broken in CI")
class ServerTest {
  companion object {
    private const val useDirectTransport = false

    private val executor: ExecutorService =
      if (useDirectTransport) {
        createDirectExecutor()
      } else {
        Executors.newCachedThreadPool()
      }

    @AfterAll
    @JvmStatic
    @Suppress("unused")
    fun afterAll() {
      executor.shutdown()
    }
  }

  private val transports: Pair<MessageTransport, MessageTransport> = run {
    if (useDirectTransport) {
      MessageTransports.direct()
    } else {
      val in1 = PipedInputStream()
      val out1 = PipedOutputStream(in1)
      val in2 = PipedInputStream()
      val out2 = PipedOutputStream(in2)
      MessageTransports.stream(in1, out2) to MessageTransports.stream(in2, out1)
    }
  }

  private val client: TestTransport = TestTransport(transports.first)
  private val server: Server = Server(transports.second)

  @BeforeEach
  fun before() {
    executor.execute { server.start() }
    executor.execute { client.start() }
  }

  @AfterEach
  fun after() {
    client.close()
    server.close()
  }

  @Test
  fun `create and close evaluator`() {
    val evaluatorId = client.sendCreateEvaluatorRequest(requestId = 123)
    client.send(CloseEvaluator(evaluatorId = evaluatorId))
  }

  @Test
  fun `evaluate module`() {
    val evaluatorId = client.sendCreateEvaluatorRequest()
    val requestId = 234L

    client.send(
      EvaluateRequest(
        requestId = requestId,
        evaluatorId = evaluatorId,
        moduleUri = URI("repl:text"),
        moduleText =
          """
        foo {
          bar = "bar"
        }
      """
            .trimIndent(),
        expr = null
      )
    )

    val response = client.receive<EvaluateResponse>()
    assertThat(response.error).isNull()
    assertThat(response.result).isNotNull
    assertThat(response.requestId).isEqualTo(requestId)

    val unpacker = MessagePack.newDefaultUnpacker(response.result)
    val value = unpacker.unpackValue()
    assertThat(value.isArrayValue)
  }

  @Test
  fun `trace logs`() {
    val evaluatorId = client.sendCreateEvaluatorRequest()

    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = URI("repl:text"),
        moduleText =
          """
        foo = trace(1 + 2 + 3)
      """
            .trimIndent(),
        expr = null
      )
    )

    val response = client.receive<LogMessage>()
    assertThat(response.level).isEqualTo(0)
    assertThat(response.message).isEqualTo("1 + 2 + 3 = 6")

    client.receive<EvaluateResponse>()
  }

  @Test
  fun `warn logs`() {
    val evaluatorId = client.sendCreateEvaluatorRequest()

    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = URI("repl:text"),
        moduleText =
          """
        @Deprecated { message = "use bar instead" }
        function foo() = 5

        result = foo()
      """
            .trimIndent(),
        expr = null
      )
    )

    val response = client.receive<LogMessage>()
    assertThat(response.level).isEqualTo(1)
    assertThat(response.message).contains("use bar instead")

    client.receive<EvaluateResponse>()
  }

  @Test
  fun `read resource`() {
    val reader =
      ResourceReaderSpec(scheme = "bahumbug", hasHierarchicalUris = true, isGlobbable = false)
    val evaluatorId = client.sendCreateEvaluatorRequest(resourceReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = URI("repl:text"),
        moduleText = """res = read("bahumbug:/foo.pkl").text""",
        expr = "res"
      )
    )

    val readResourceMsg = client.receive<ReadResourceRequest>()
    assertThat(readResourceMsg.uri.toString()).isEqualTo("bahumbug:/foo.pkl")
    assertThat(readResourceMsg.evaluatorId).isEqualTo(evaluatorId)

    client.send(
      ReadResourceResponse(
        requestId = readResourceMsg.requestId,
        evaluatorId = evaluatorId,
        contents = "my bahumbug".toByteArray(),
        error = null
      )
    )

    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.error).isNull()

    val unpacker = MessagePack.newDefaultUnpacker(evaluateResponse.result)
    val value = unpacker.unpackValue()
    assertThat(value.asStringValue().asString()).isEqualTo("my bahumbug")
  }

  @Test
  fun `read resource error`() {
    val reader =
      ResourceReaderSpec(scheme = "bahumbug", hasHierarchicalUris = true, isGlobbable = false)
    val evaluatorId = client.sendCreateEvaluatorRequest(resourceReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = URI("repl:text"),
        moduleText = """res = read("bahumbug:/foo.txt").text""",
        expr = "res"
      )
    )

    val readResourceMsg = client.receive<ReadResourceRequest>()

    client.send(
      ReadResourceResponse(
        requestId = readResourceMsg.requestId,
        evaluatorId = evaluatorId,
        contents = null,
        error = "cannot read my bahumbug"
      )
    )

    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.error).contains("bahumbug:/foo.txt")
    assertThat(evaluateResponse.error).doesNotContain("org.pkl.core.PklBugException")
  }

  @Test
  fun `glob resource`() {
    val reader = ResourceReaderSpec(scheme = "bird", hasHierarchicalUris = true, isGlobbable = true)
    val evaluatorId = client.sendCreateEvaluatorRequest(resourceReaders = listOf(reader))
    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = URI("repl:text"),
        moduleText =
          """
        res = read*("bird:/**.txt").keys
      """
            .trimIndent(),
        expr = "res"
      )
    )
    val listResourcesRequest = client.receive<ListResourcesRequest>()
    assertThat(listResourcesRequest.uri.toString()).isEqualTo("bird:/")
    client.send(
      ListResourcesResponse(
        requestId = listResourcesRequest.requestId,
        evaluatorId = listResourcesRequest.evaluatorId,
        pathElements = listOf(PathElement("foo.txt", false), PathElement("subdir", true)),
        error = null
      )
    )
    val listResourcesRequest2 = client.receive<ListResourcesRequest>()
    assertThat(listResourcesRequest2.uri.toString()).isEqualTo("bird:/subdir/")
    client.send(
      ListResourcesResponse(
        requestId = listResourcesRequest2.requestId,
        evaluatorId = listResourcesRequest2.evaluatorId,
        pathElements =
          listOf(
            PathElement("bar.txt", false),
          ),
        error = null
      )
    )
    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.result!!.debugYaml)
      .isEqualTo(
        """
      - 6
      - 
        - bird:/foo.txt
        - bird:/subdir/bar.txt
    """
          .trimIndent()
      )
  }

  @Test
  fun `glob resource error`() {
    val reader = ResourceReaderSpec(scheme = "bird", hasHierarchicalUris = true, isGlobbable = true)
    val evaluatorId = client.sendCreateEvaluatorRequest(resourceReaders = listOf(reader))
    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = URI("repl:text"),
        moduleText =
          """
        res = read*("bird:/**.txt").keys
      """
            .trimIndent(),
        expr = "res"
      )
    )
    val listResourcesRequest = client.receive<ListResourcesRequest>()
    assertThat(listResourcesRequest.uri.toString()).isEqualTo("bird:/")
    client.send(
      ListResourcesResponse(
        requestId = listResourcesRequest.requestId,
        evaluatorId = listResourcesRequest.evaluatorId,
        pathElements = null,
        error = "didnt work"
      )
    )
    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.error)
      .isEqualTo(
        """
      –– Pkl Error ––
      I/O error resolving glob pattern `bird:/**.txt`.
      IOException: didnt work

      1 | res = read*("bird:/**.txt").keys
                ^^^^^^^^^^^^^^^^^^^^^
      at text#res (repl:text)

      1 | res
          ^^^
      at  (repl:text)
      
      """
          .trimIndent()
      )
  }

  @Test
  fun `read module`() {
    val reader =
      ModuleReaderSpec(
        scheme = "bird",
        hasHierarchicalUris = true,
        isLocal = true,
        isGlobbable = false
      )
    val evaluatorId = client.sendCreateEvaluatorRequest(moduleReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = URI("repl:text"),
        moduleText = """res = import("bird:/pigeon.pkl").value""",
        expr = "res"
      )
    )

    val readModuleMsg = client.receive<ReadModuleRequest>()
    assertThat(readModuleMsg.uri.toString()).isEqualTo("bird:/pigeon.pkl")
    assertThat(readModuleMsg.evaluatorId).isEqualTo(evaluatorId)

    client.send(
      ReadModuleResponse(
        requestId = readModuleMsg.requestId,
        evaluatorId = evaluatorId,
        contents = "value = 5",
        error = null
      )
    )

    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.error).isNull()
    val unpacker = MessagePack.newDefaultUnpacker(evaluateResponse.result)
    val value = unpacker.unpackValue()
    assertThat(value.asIntegerValue().asInt()).isEqualTo(5)
  }

  @Test
  fun `read module error`() {
    val reader =
      ModuleReaderSpec(
        scheme = "bird",
        hasHierarchicalUris = true,
        isLocal = true,
        isGlobbable = false
      )
    val evaluatorId = client.sendCreateEvaluatorRequest(moduleReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = URI("repl:text"),
        moduleText = """res = import("bird:/pigeon.pkl").value""",
        expr = "res"
      )
    )

    val readModuleMsg = client.receive<ReadModuleRequest>()
    assertThat(readModuleMsg.uri.toString()).isEqualTo("bird:/pigeon.pkl")
    assertThat(readModuleMsg.evaluatorId).isEqualTo(evaluatorId)

    client.send(
      ReadModuleResponse(
        requestId = readModuleMsg.requestId,
        evaluatorId = evaluatorId,
        contents = null,
        error = "Don't know where Pigeon is"
      )
    )

    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.error).contains("Don't know where Pigeon is")
  }

  @Test
  fun `glob module`() {
    val reader =
      ModuleReaderSpec(
        scheme = "bird",
        hasHierarchicalUris = true,
        isLocal = true,
        isGlobbable = true
      )
    val evaluatorId = client.sendCreateEvaluatorRequest(moduleReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = URI("repl:text"),
        moduleText = """res = import*("bird:/**.pkl").keys""",
        expr = "res"
      )
    )

    val listModulesMsg = client.receive<ListModulesRequest>()
    assertThat(listModulesMsg.uri.scheme).isEqualTo("bird")
    assertThat(listModulesMsg.uri.path).isEqualTo("/")
    client.send(
      ListModulesResponse(
        requestId = listModulesMsg.requestId,
        evaluatorId = evaluatorId,
        pathElements =
          listOf(
            PathElement("birds", true),
            PathElement("majesticBirds", true),
            PathElement("Person.pkl", false)
          ),
        error = null
      )
    )
    val listModulesMsg2 = client.receive<ListModulesRequest>()
    assertThat(listModulesMsg2.uri.scheme).isEqualTo("bird")
    assertThat(listModulesMsg2.uri.path).isEqualTo("/birds/")
    client.send(
      ListModulesResponse(
        requestId = listModulesMsg2.requestId,
        evaluatorId = listModulesMsg2.evaluatorId,
        pathElements =
          listOf(
            PathElement("pigeon.pkl", false),
            PathElement("parrot.pkl", false),
          ),
        error = null
      )
    )
    val listModulesMsg3 = client.receive<ListModulesRequest>()
    assertThat(listModulesMsg3.uri.scheme).isEqualTo("bird")
    assertThat(listModulesMsg3.uri.path).isEqualTo("/majesticBirds/")
    client.send(
      ListModulesResponse(
        requestId = listModulesMsg3.requestId,
        evaluatorId = listModulesMsg3.evaluatorId,
        pathElements =
          listOf(
            PathElement("barnOwl.pkl", false),
            PathElement("elfOwl.pkl", false),
          ),
        error = null
      )
    )

    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.result!!.debugRendering)
      .isEqualTo(
        """
      - 6
      - 
        - bird:/Person.pkl
        - bird:/birds/parrot.pkl
        - bird:/birds/pigeon.pkl
        - bird:/majesticBirds/barnOwl.pkl
        - bird:/majesticBirds/elfOwl.pkl
    """
          .trimIndent()
      )
  }

  @Test
  fun `glob module error`() {
    val reader =
      ModuleReaderSpec(
        scheme = "bird",
        hasHierarchicalUris = true,
        isLocal = true,
        isGlobbable = true
      )
    val evaluatorId = client.sendCreateEvaluatorRequest(moduleReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = URI("repl:text"),
        moduleText = """res = import*("bird:/**.pkl").keys""",
        expr = "res"
      )
    )

    val listModulesMsg = client.receive<ListModulesRequest>()
    assertThat(listModulesMsg.uri.scheme).isEqualTo("bird")
    assertThat(listModulesMsg.uri.path).isEqualTo("/")
    client.send(
      ListModulesResponse(
        requestId = listModulesMsg.requestId,
        evaluatorId = evaluatorId,
        pathElements = null,
        error = "nope"
      )
    )
    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.error)
      .isEqualTo(
        """
      –– Pkl Error ––
      I/O error resolving glob pattern `bird:/**.pkl`.
      IOException: nope

      1 | res = import*("bird:/**.pkl").keys
                ^^^^^^^^^^^^^^^^^^^^^^^
      at text#res (repl:text)

      1 | res
          ^^^
      at  (repl:text)
      
      """
          .trimIndent()
      )
  }

  @Test
  fun `read and evaluate module path from jar`(@TempDir tempDir: Path) {
    val jarFile = tempDir.resolve("resource1.jar")
    jarFile.outputStream().use { outStream ->
      javaClass.getResourceAsStream("resource1.jar")!!.use { inStream ->
        inStream.copyTo(outStream)
      }
    }

    val evaluatorId = client.sendCreateEvaluatorRequest(modulePaths = listOf(jarFile))

    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = URI("modulepath:/dir1/module.pkl"),
        moduleText = null,
        expr = "output.text"
      )
    )

    val response = client.receive<EvaluateResponse>()
    assertThat(response.error).isNull()
    val tripleQuote = "\"\"\""
    assertThat(response.result!!.debugYaml)
      .isEqualTo(
        """
      |
        res1 {
          uri = "modulepath:/dir1/resource1.txt"
          text = $tripleQuote
            content
            
            $tripleQuote
          base64 = "Y29udGVudAo="
        }
        res2 {
          uri = "modulepath:/dir1/resource1.txt"
          text = $tripleQuote
            content
            
            $tripleQuote
          base64 = "Y29udGVudAo="
        }
        res3 {
          ressy = "the module2 output"
        }
    """
          .trimIndent()
      )
  }

  @Test
  fun `import triple-dot path`() {
    val reader =
      ModuleReaderSpec(
        scheme = "bird",
        hasHierarchicalUris = true,
        isLocal = true,
        isGlobbable = true
      )
    val evaluatorId = client.sendCreateEvaluatorRequest(moduleReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = URI("bird:/foo/bar/baz.pkl"),
        moduleText =
          """
        import ".../buz.pkl"
        
        res = buz.res
      """
            .trimIndent(),
        expr = "res"
      )
    )
    val readModuleRequest = client.receive<ReadModuleRequest>()
    assertThat(readModuleRequest.uri).isEqualTo(URI("bird:/foo/buz.pkl"))
    client.send(
      ReadModuleResponse(
        requestId = readModuleRequest.requestId,
        evaluatorId = readModuleRequest.evaluatorId,
        contents = null,
        error = "not here"
      )
    )

    val readModuleRequest2 = client.receive<ReadModuleRequest>()
    assertThat(readModuleRequest2.uri).isEqualTo(URI("bird:/buz.pkl"))
    client.send(
      ReadModuleResponse(
        requestId = readModuleRequest2.requestId,
        evaluatorId = readModuleRequest2.evaluatorId,
        contents = "res = 1",
        error = null
      )
    )

    val evaluatorResponse = client.receive<EvaluateResponse>()
    assertThat(evaluatorResponse.result!!.debugYaml).isEqualTo("1")
  }

  @Test
  fun `evaluate error`() {
    val evaluatorId = client.sendCreateEvaluatorRequest()

    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = URI("repl:text"),
        moduleText = """foo = 1""",
        expr = "foo as String"
      )
    )

    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.requestId).isEqualTo(1)
    assertThat(evaluateResponse.error).contains("Expected value of type")
  }

  @Test
  fun `evaluate client-provided module reader`() {
    val reader =
      ModuleReaderSpec(
        scheme = "bird",
        hasHierarchicalUris = true,
        isLocal = false,
        isGlobbable = false
      )
    val evaluatorId = client.sendCreateEvaluatorRequest(moduleReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = URI("bird:/pigeon.pkl"),
        moduleText = null,
        expr = "output.text",
      )
    )

    val readModuleRequest = client.receive<ReadModuleRequest>()
    assertThat(readModuleRequest.uri.toString()).isEqualTo("bird:/pigeon.pkl")

    client.send(
      ReadModuleResponse(
        requestId = readModuleRequest.requestId,
        evaluatorId = evaluatorId,
        contents =
          """
          firstName = "Pigeon"
          lastName = "Bird"
          fullName = firstName + " " + lastName
        """
            .trimIndent(),
        error = null
      )
    )

    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.result).isNotNull
    assertThat(evaluateResponse.result!!.debugYaml)
      .isEqualTo(
        """
        |
          firstName = "Pigeon"
          lastName = "Bird"
          fullName = "Pigeon Bird"
      """
          .trimIndent()
      )
  }

  @Test
  fun `concurrent evaluations`() {
    val reader =
      ModuleReaderSpec(
        scheme = "bird",
        hasHierarchicalUris = true,
        isLocal = false,
        isGlobbable = false
      )
    val evaluatorId = client.sendCreateEvaluatorRequest(moduleReaders = listOf(reader))
    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = URI("bird:/pigeon.pkl"),
        moduleText = null,
        expr = "output.text",
      )
    )

    client.send(
      EvaluateRequest(
        requestId = 2,
        evaluatorId = evaluatorId,
        moduleUri = URI("bird:/parrot.pkl"),
        moduleText = null,
        expr = "output.text"
      )
    )

    // evaluation is single-threaded; `parrot.pkl` gets evaluated after `pigeon.pkl` completes.
    val response11 = client.receive<ReadModuleRequest>()
    assertThat(response11.uri.toString()).isEqualTo("bird:/pigeon.pkl")

    client.send(
      ReadModuleResponse(
        response11.requestId,
        evaluatorId,
        contents =
          """
          firstName = "Pigeon"
          lastName = "Bird"
          fullName = firstName + " " + lastName
        """
            .trimIndent(),
        error = null
      )
    )

    val response12 = client.receive<EvaluateResponse>()
    assertThat(response12.result).isNotNull
    assertThat(response12.result!!.debugYaml)
      .isEqualTo(
        """
        |
          firstName = "Pigeon"
          lastName = "Bird"
          fullName = "Pigeon Bird"
      """
          .trimIndent()
      )

    val response21 = client.receive<ReadModuleRequest>()
    assertThat(response21.uri.toString()).isEqualTo("bird:/parrot.pkl")

    client.send(
      ReadModuleResponse(
        response21.requestId,
        evaluatorId,
        contents =
          """
          firstName = "Parrot"
          lastName = "Bird"
          fullName = firstName + " " + lastName
        """
            .trimIndent(),
        error = null
      )
    )

    val response22 = client.receive<EvaluateResponse>()
    assertThat(response22.result).isNotNull
    assertThat(response22.result!!.debugYaml)
      .isEqualTo(
        """
        |
          firstName = "Parrot"
          lastName = "Bird"
          fullName = "Parrot Bird"
      """
          .trimIndent()
      )
  }

  @Test
  fun `evaluate with project dependencies`(@TempDir tempDir: Path) {
    val cacheDir = tempDir.resolve("cache").createDirectories()
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
    val evaluatorId =
      client.sendCreateEvaluatorRequest(
        cacheDir = cacheDir,
        project =
          Project(
            projectFileUri = projectDir.resolve("PklProject").toUri(),
            packageUri = null,
            dependencies =
              mapOf(
                "birds" to
                  RemoteDependency(packageUri = URI("package://localhost:12110/birds@0.5.0"), null),
                "lib" to
                  Project(
                    projectFileUri = libDir.toUri().resolve("PklProject"),
                    packageUri = URI("package://localhost:12110/lib@5.0.0"),
                    dependencies = emptyMap()
                  )
              )
          )
      )
    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = module.toUri(),
        moduleText = null,
        expr = "output.text",
      )
    )
    val resp2 = client.receive<EvaluateResponse>()
    assertThat(resp2.error).isNull()
    assertThat(resp2.result).isNotNull()
    assertThat(resp2.result!!.debugRendering.trim())
      .isEqualTo(
        """
        |
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

  private val ByteArray.debugYaml
    get() = MessagePackDebugRenderer(this).output.trimIndent()

  private fun TestTransport.sendCreateEvaluatorRequest(
    requestId: Long = 123,
    resourceReaders: List<ResourceReaderSpec> = listOf(),
    moduleReaders: List<ModuleReaderSpec> = listOf(),
    modulePaths: List<Path> = listOf(),
    project: Project? = null,
    cacheDir: Path? = null
  ): Long {
    val message =
      CreateEvaluatorRequest(
        requestId = 123,
        allowedResources = listOf(Pattern.compile(".*")),
        allowedModules = listOf(Pattern.compile(".*")),
        clientResourceReaders = resourceReaders,
        clientModuleReaders = moduleReaders,
        modulePaths = modulePaths,
        env = mapOf(),
        properties = mapOf(),
        timeout = null,
        rootDir = null,
        cacheDir = cacheDir,
        outputFormat = null,
        project = project
      )

    send(message)

    val response = receive<CreateEvaluatorResponse>()
    assertThat(response.requestId).isEqualTo(requestId)
    assertThat(response.evaluatorId).isNotNull
    assertThat(response.error).isNull()

    return response.evaluatorId!!
  }
}
