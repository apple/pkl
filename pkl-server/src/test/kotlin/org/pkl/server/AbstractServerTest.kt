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
package org.pkl.server

import java.net.URI
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.msgpack.core.MessagePack
import org.pkl.commons.test.PackageServer
import org.pkl.core.messaging.Messages.*
import org.pkl.core.module.PathElement

abstract class AbstractServerTest {

  companion object {
    /** Set to `true` to bypass messagepack serialization when running [JvmServerTest]. */
    internal const val USE_DIRECT_TRANSPORT = false
    lateinit var executor: ExecutorService

    @BeforeAll
    @JvmStatic
    fun beforeAll() {
      executor =
        if (USE_DIRECT_TRANSPORT) {
          createDirectExecutor()
        } else {
          Executors.newCachedThreadPool()
        }
    }

    @AfterAll
    @JvmStatic
    fun afterAll() {
      executor.shutdown()
    }
  }

  abstract val client: TestTransport

  private val blankCreateEvaluatorRequest =
    CreateEvaluatorRequest(
      requestId = 1,
      http = null,
      allowedModules = null,
      allowedResources = null,
      clientModuleReaders = null,
      clientResourceReaders = null,
      modulePaths = null,
      env = null,
      properties = null,
      timeout = null,
      rootDir = null,
      cacheDir = null,
      outputFormat = null,
      project = null,
      externalModuleReaders = null,
      externalResourceReaders = null,
      traceMode = null,
    )

  @Test
  fun `create and close evaluator`() {
    val evaluatorId = client.sendCreateEvaluatorRequest(123)
    client.send(CloseEvaluator(evaluatorId))
  }

  @Test
  fun `evaluate module`() {
    val evaluatorId = client.sendCreateEvaluatorRequest()
    val requestId = 234L

    client.send(
      EvaluateRequest(
        requestId,
        evaluatorId,
        URI("repl:text"),
        """
        foo {
          bar = "bar"
        }
      """
          .trimIndent(),
        null,
      )
    )

    val response = client.receive<EvaluateResponse>()
    assertThat(response.error).isNull()
    assertThat(response.result).isNotNull
    assertThat(response.requestId()).isEqualTo(requestId)

    val unpacker = MessagePack.newDefaultUnpacker(response.result)
    val value = unpacker.unpackValue()
    assertThat(value.isArrayValue)
  }

  @Test
  fun `trace logs`() {
    val evaluatorId = client.sendCreateEvaluatorRequest()

    client.send(
      EvaluateRequest(
        1,
        evaluatorId,
        URI("repl:text"),
        """
        foo = trace(1 + 2 + 3)
      """
          .trimIndent(),
        null,
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
        1,
        evaluatorId,
        URI("repl:text"),
        """
        @Deprecated { message = "use bar instead" }
        function foo() = 5

        result = foo()
      """
          .trimIndent(),
        null,
      )
    )

    val response = client.receive<LogMessage>()
    assertThat(response.level).isEqualTo(1)
    assertThat(response.message).contains("use bar instead")

    client.receive<EvaluateResponse>()
  }

  @Test
  fun `read resource`() {
    val reader = ResourceReaderSpec("bahumbug", true, false)
    val evaluatorId = client.sendCreateEvaluatorRequest(resourceReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        1,
        evaluatorId,
        URI("repl:text"),
        """res = read("bahumbug:/foo.pkl").text""",
        "res",
      )
    )

    val readResourceMsg = client.receive<ReadResourceRequest>()
    assertThat(readResourceMsg.uri.toString()).isEqualTo("bahumbug:/foo.pkl")
    assertThat(readResourceMsg.evaluatorId).isEqualTo(evaluatorId)

    client.send(
      ReadResourceResponse(
        readResourceMsg.requestId,
        evaluatorId,
        "my bahumbug".toByteArray(),
        null,
      )
    )

    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.error).isNull()

    val unpacker = MessagePack.newDefaultUnpacker(evaluateResponse.result)
    val value = unpacker.unpackValue()
    assertThat(value.asStringValue().asString()).isEqualTo("my bahumbug")
  }

  @Disabled(
    "Unable to construct ReadResourceResponse with null contents due to Kotlin compiler bug"
  )
  @Test
  fun `read resource -- null contents and null error`() {
    val reader = ResourceReaderSpec("bahumbug", true, false)
    val evaluatorId = client.sendCreateEvaluatorRequest(resourceReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = URI("repl:text"),
        moduleText = """res = read("bahumbug:/foo.pkl").text""",
        expr = "res",
      )
    )

    val readResourceMsg = client.receive<ReadResourceRequest>()
    assertThat(readResourceMsg.uri.toString()).isEqualTo("bahumbug:/foo.pkl")
    assertThat(readResourceMsg.evaluatorId).isEqualTo(evaluatorId)

    client.send(ReadResourceResponse(readResourceMsg.requestId, evaluatorId, byteArrayOf(), null))
    // for this test to be correct this should actually be:
    // client.send(ReadResourceResponse(readResourceMsg.requestId, evaluatorId, null, null))
    // this should be evaluated again once https://github.com/apple/pkl/issues/698 is addressed
    // see conversation here https://github.com/apple/pkl/pull/660#discussion_r1819545811

    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.error).isNull()

    val unpacker = MessagePack.newDefaultUnpacker(evaluateResponse.result)
    val value = unpacker.unpackValue()
    assertThat(value.asStringValue().asString()).isEqualTo("")
  }

  @Test
  fun `read resource error`() {
    val reader = ResourceReaderSpec("bahumbug", true, false)
    val evaluatorId = client.sendCreateEvaluatorRequest(resourceReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        1,
        evaluatorId,
        URI("repl:text"),
        """res = read("bahumbug:/foo.txt").text""",
        "res",
      )
    )

    val readResourceMsg = client.receive<ReadResourceRequest>()

    client.send(
      ReadResourceResponse(
        readResourceMsg.requestId,
        evaluatorId,
        byteArrayOf(),
        "cannot read my bahumbug",
      )
    )

    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.error).contains("bahumbug:/foo.txt")
    assertThat(evaluateResponse.error).doesNotContain("org.pkl.core.PklBugException")
  }

  @Test
  fun `glob resource`() {
    val reader = ResourceReaderSpec("bird", true, true)
    val evaluatorId = client.sendCreateEvaluatorRequest(resourceReaders = listOf(reader))
    client.send(
      EvaluateRequest(
        1,
        evaluatorId,
        URI("repl:text"),
        """
        res = read*("bird:/**.txt").keys
      """
          .trimIndent(),
        "res",
      )
    )
    val listResourcesRequest = client.receive<ListResourcesRequest>()
    assertThat(listResourcesRequest.uri.toString()).isEqualTo("bird:/")
    client.send(
      ListResourcesResponse(
        listResourcesRequest.requestId,
        listResourcesRequest.evaluatorId,
        listOf(PathElement("foo.txt", false), PathElement("subdir", true)),
        null,
      )
    )
    val listResourcesRequest2 = client.receive<ListResourcesRequest>()
    assertThat(listResourcesRequest2.uri.toString()).isEqualTo("bird:/subdir/")
    client.send(
      ListResourcesResponse(
        listResourcesRequest2.requestId,
        listResourcesRequest2.evaluatorId,
        listOf(PathElement("bar.txt", false)),
        null,
      )
    )
    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.result?.debugRendering)
      .isEqualTo(
        """
      - 6
      - 
        - 'bird:/foo.txt'
        - 'bird:/subdir/bar.txt'
    """
          .trimIndent()
      )
  }

  @Test
  fun `glob resources -- null pathElements and null error`() {
    val reader = ResourceReaderSpec("bird", true, true)
    val evaluatorId = client.sendCreateEvaluatorRequest(resourceReaders = listOf(reader))
    client.send(
      EvaluateRequest(
        1,
        evaluatorId,
        URI("repl:text"),
        """
        res = read*("bird:/**.txt").keys
      """
          .trimIndent(),
        "res",
      )
    )
    val listResourcesRequest = client.receive<ListResourcesRequest>()
    client.send(
      ListResourcesResponse(
        listResourcesRequest.requestId,
        listResourcesRequest.evaluatorId,
        null,
        null,
      )
    )
    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.result?.debugRendering)
      .isEqualTo(
        """
        - 6
        - []
        """
          .trimIndent()
      )
  }

  @Test
  fun `glob resource error`() {
    val reader = ResourceReaderSpec("bird", true, true)
    val evaluatorId = client.sendCreateEvaluatorRequest(resourceReaders = listOf(reader))
    client.send(
      EvaluateRequest(
        1,
        evaluatorId,
        URI("repl:text"),
        """
        res = read*("bird:/**.txt").keys
      """
          .trimIndent(),
        "res",
      )
    )
    val listResourcesRequest = client.receive<ListResourcesRequest>()
    assertThat(listResourcesRequest.uri.toString()).isEqualTo("bird:/")
    client.send(
      ListResourcesResponse(
        listResourcesRequest.requestId,
        listResourcesRequest.evaluatorId,
        null,
        "didnt work",
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
    val reader = ModuleReaderSpec("bird", true, true, false)
    val evaluatorId = client.sendCreateEvaluatorRequest(moduleReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        1,
        evaluatorId,
        URI("repl:text"),
        """res = import("bird:/pigeon.pkl").value""",
        "res",
      )
    )

    val readModuleMsg = client.receive<ReadModuleRequest>()
    assertThat(readModuleMsg.uri.toString()).isEqualTo("bird:/pigeon.pkl")
    assertThat(readModuleMsg.evaluatorId).isEqualTo(evaluatorId)

    client.send(ReadModuleResponse(readModuleMsg.requestId, evaluatorId, "value = 5", null))

    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.error).isNull()
    val unpacker = MessagePack.newDefaultUnpacker(evaluateResponse.result)
    val value = unpacker.unpackValue()
    assertThat(value.asIntegerValue().asInt()).isEqualTo(5)
  }

  @Test
  fun `read module -- null contents and null error`() {
    val reader = ModuleReaderSpec("bird", true, true, false)
    val evaluatorId = client.sendCreateEvaluatorRequest(moduleReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        requestId = 1,
        evaluatorId = evaluatorId,
        moduleUri = URI("repl:text"),
        moduleText = """res = import("bird:/pigeon.pkl")""",
        expr = "res",
      )
    )

    val readModuleMsg = client.receive<ReadModuleRequest>()
    assertThat(readModuleMsg.uri.toString()).isEqualTo("bird:/pigeon.pkl")
    assertThat(readModuleMsg.evaluatorId).isEqualTo(evaluatorId)

    client.send(ReadModuleResponse(readModuleMsg.requestId, evaluatorId, null, null))

    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.error).isNull()
    val unpacker = MessagePack.newDefaultUnpacker(evaluateResponse.result)
    val value = unpacker.unpackValue().asArrayValue().list()
    assertThat(value[0].asIntegerValue().asLong()).isEqualTo(0x1)
    assertThat(value[1].asStringValue().asString()).isEqualTo("pigeon")
    assertThat(value[3].asArrayValue().list()).isEmpty()
  }

  @Test
  fun `read module error`() {
    val reader = ModuleReaderSpec("bird", true, true, false)
    val evaluatorId = client.sendCreateEvaluatorRequest(moduleReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        1,
        evaluatorId,
        URI("repl:text"),
        """res = import("bird:/pigeon.pkl").value""",
        "res",
      )
    )

    val readModuleMsg = client.receive<ReadModuleRequest>()
    assertThat(readModuleMsg.uri.toString()).isEqualTo("bird:/pigeon.pkl")
    assertThat(readModuleMsg.evaluatorId).isEqualTo(evaluatorId)

    client.send(
      ReadModuleResponse(readModuleMsg.requestId, evaluatorId, null, "Don't know where Pigeon is")
    )

    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.error).contains("Don't know where Pigeon is")
  }

  @Test
  fun `glob module`() {
    val reader = ModuleReaderSpec("bird", true, true, true)
    val evaluatorId = client.sendCreateEvaluatorRequest(moduleReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        1,
        evaluatorId,
        URI("repl:text"),
        """res = import*("bird:/**.pkl").keys""",
        "res",
      )
    )

    val listModulesMsg = client.receive<ListModulesRequest>()
    assertThat(listModulesMsg.uri.scheme).isEqualTo("bird")
    assertThat(listModulesMsg.uri.path).isEqualTo("/")
    client.send(
      ListModulesResponse(
        listModulesMsg.requestId,
        evaluatorId,
        listOf(
          PathElement("birds", true),
          PathElement("majesticBirds", true),
          PathElement("Person.pkl", false),
        ),
        null,
      )
    )
    val listModulesMsg2 = client.receive<ListModulesRequest>()
    assertThat(listModulesMsg2.uri.scheme).isEqualTo("bird")
    assertThat(listModulesMsg2.uri.path).isEqualTo("/birds/")
    client.send(
      ListModulesResponse(
        listModulesMsg2.requestId,
        listModulesMsg2.evaluatorId,
        listOf(PathElement("pigeon.pkl", false), PathElement("parrot.pkl", false)),
        null,
      )
    )
    val listModulesMsg3 = client.receive<ListModulesRequest>()
    assertThat(listModulesMsg3.uri.scheme).isEqualTo("bird")
    assertThat(listModulesMsg3.uri.path).isEqualTo("/majesticBirds/")
    client.send(
      ListModulesResponse(
        listModulesMsg3.requestId,
        listModulesMsg3.evaluatorId,
        listOf(PathElement("barnOwl.pkl", false), PathElement("elfOwl.pkl", false)),
        null,
      )
    )

    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.result?.debugRendering)
      .isEqualTo(
        """
      - 6
      - 
        - 'bird:/Person.pkl'
        - 'bird:/birds/parrot.pkl'
        - 'bird:/birds/pigeon.pkl'
        - 'bird:/majesticBirds/barnOwl.pkl'
        - 'bird:/majesticBirds/elfOwl.pkl'
    """
          .trimIndent()
      )
  }

  @Test
  fun `glob module -- null pathElements and null error`() {
    val reader = ModuleReaderSpec("bird", true, true, true)
    val evaluatorId = client.sendCreateEvaluatorRequest(moduleReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        1,
        evaluatorId,
        URI("repl:text"),
        """res = import*("bird:/**.pkl").keys""",
        "res",
      )
    )
    val listModulesMsg = client.receive<ListModulesRequest>()
    client.send(ListModulesResponse(listModulesMsg.requestId, evaluatorId, null, null))

    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.result?.debugRendering)
      .isEqualTo(
        """
      - 6
      - []
    """
          .trimIndent()
      )
  }

  @Test
  fun `glob module error`() {
    val reader = ModuleReaderSpec("bird", true, true, true)
    val evaluatorId = client.sendCreateEvaluatorRequest(moduleReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        1,
        evaluatorId,
        URI("repl:text"),
        """res = import*("bird:/**.pkl").keys""",
        "res",
      )
    )

    val listModulesMsg = client.receive<ListModulesRequest>()
    assertThat(listModulesMsg.uri.scheme).isEqualTo("bird")
    assertThat(listModulesMsg.uri.path).isEqualTo("/")
    client.send(ListModulesResponse(listModulesMsg.requestId, evaluatorId, null, "nope"))
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
      EvaluateRequest(1, evaluatorId, URI("modulepath:/dir1/module.pkl"), null, "output.text")
    )

    val response = client.receive<EvaluateResponse>()
    assertThat(response.error).isNull()
    val tripleQuote = "\"\"\""
    assertThat(response.result?.debugRendering)
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
    val reader = ModuleReaderSpec("bird", true, true, true)
    val evaluatorId = client.sendCreateEvaluatorRequest(moduleReaders = listOf(reader))

    client.send(
      EvaluateRequest(
        1,
        evaluatorId,
        URI("bird:/foo/bar/baz.pkl"),
        """
        import ".../buz.pkl"
        
        res = buz.res
      """
          .trimIndent(),
        "res",
      )
    )
    val readModuleRequest = client.receive<ReadModuleRequest>()
    assertThat(readModuleRequest.uri).isEqualTo(URI("bird:/foo/buz.pkl"))
    client.send(
      ReadModuleResponse(
        readModuleRequest.requestId,
        readModuleRequest.evaluatorId,
        null,
        "not here",
      )
    )

    val readModuleRequest2 = client.receive<ReadModuleRequest>()
    assertThat(readModuleRequest2.uri).isEqualTo(URI("bird:/buz.pkl"))
    client.send(
      ReadModuleResponse(
        readModuleRequest2.requestId,
        readModuleRequest2.evaluatorId,
        "res = 1",
        null,
      )
    )

    val evaluatorResponse = client.receive<EvaluateResponse>()
    assertThat(evaluatorResponse.result?.debugRendering).isEqualTo("1")
  }

  @Test
  fun `evaluate error`() {
    val evaluatorId = client.sendCreateEvaluatorRequest()

    client.send(EvaluateRequest(1, evaluatorId, URI("repl:text"), """foo = 1""", "foo as String"))

    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.requestId()).isEqualTo(1)
    assertThat(evaluateResponse.error).contains("Expected value of type")
  }

  @Test
  fun `evaluate client-provided module reader`() {
    val reader = ModuleReaderSpec("bird", true, false, false)
    val evaluatorId = client.sendCreateEvaluatorRequest(moduleReaders = listOf(reader))

    client.send(EvaluateRequest(1, evaluatorId, URI("bird:/pigeon.pkl"), null, "output.text"))

    val readModuleRequest = client.receive<ReadModuleRequest>()
    assertThat(readModuleRequest.uri.toString()).isEqualTo("bird:/pigeon.pkl")

    client.send(
      ReadModuleResponse(
        readModuleRequest.requestId,
        evaluatorId,
        """
          firstName = "Pigeon"
          lastName = "Bird"
          fullName = firstName + " " + lastName
        """
          .trimIndent(),
        null,
      )
    )

    val evaluateResponse = client.receive<EvaluateResponse>()
    assertThat(evaluateResponse.result).isNotNull
    assertThat(evaluateResponse.result?.debugRendering)
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
    val reader = ModuleReaderSpec("bird", true, false, false)
    val evaluatorId = client.sendCreateEvaluatorRequest(moduleReaders = listOf(reader))
    client.send(EvaluateRequest(1, evaluatorId, URI("bird:/pigeon.pkl"), null, "output.text"))

    client.send(EvaluateRequest(2, evaluatorId, URI("bird:/parrot.pkl"), null, "output.text"))

    // evaluation is single-threaded; `parrot.pkl` gets evaluated after `pigeon.pkl` completes.
    val response11 = client.receive<ReadModuleRequest>()
    assertThat(response11.uri.toString()).isEqualTo("bird:/pigeon.pkl")

    client.send(
      ReadModuleResponse(
        response11.requestId,
        evaluatorId,
        """
          firstName = "Pigeon"
          lastName = "Bird"
          fullName = firstName + " " + lastName
        """
          .trimIndent(),
        null,
      )
    )

    val response12 = client.receive<EvaluateResponse>()
    assertThat(response12.result).isNotNull
    assertThat(response12.result?.debugRendering)
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
        """
          firstName = "Parrot"
          lastName = "Bird"
          fullName = firstName + " " + lastName
        """
          .trimIndent(),
        null,
      )
    )

    val response22 = client.receive<EvaluateResponse>()
    assertThat(response22.result).isNotNull
    assertThat(response22.result?.debugRendering)
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
        baseUri = "package://localhost:0/lib"
        version = "5.0.0"
        packageZipUrl = "https://localhost:0/lib.zip"
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
          "package://localhost:0/birds@0": {
            "type": "remote",
            "uri": "projectpackage://localhost:0/birds@0.5.0",
            "checksums": {
              "sha256": "${dollar}skipChecksumVerification"
            }
          },
          "package://localhost:0/fruit@1": {
            "type": "remote",
            "uri": "projectpackage://localhost:0/fruit@1.0.5",
            "checksums": {
              "sha256": "${dollar}skipChecksumVerification"
            }
          },
          "package://localhost:0/lib@5": {
            "type": "local",
            "uri": "projectpackage://localhost:0/lib@5.0.0",
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
            projectDir.resolve("PklProject").toUri(),
            null,
            mapOf(
              "birds" to RemoteDependency(URI("package://localhost:0/birds@0.5.0"), null),
              "lib" to
                Project(
                  libDir.toUri().resolve("PklProject"),
                  URI("package://localhost:0/lib@5.0.0"),
                  emptyMap(),
                ),
            ),
          ),
      )
    client.send(EvaluateRequest(1, evaluatorId, module.toUri(), null, "output.text"))
    val resp2 = client.receive<EvaluateResponse>()
    assertThat(resp2.error).isNull()
    assertThat(resp2.result).isNotNull()
    assertThat(resp2.result?.debugRendering?.trim())
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

  @Test
  fun `http rewrites`() {
    val evaluatorId =
      client.sendCreateEvaluatorRequest(
        http =
          Http(
            caCertificates = null,
            proxy = null,
            rewrites = mapOf(URI("https://example.com/") to URI("https://example.example/")),
          )
      )
    client.send(
      EvaluateRequest(
        1,
        evaluatorId,
        URI("repl:text"),
        "res = import(\"https://example.com/foo.pkl\")",
        "output.text",
      )
    )
    val response = client.receive<EvaluateResponse>()
    assertThat(response.error)
      .contains(
        "request was rewritten: https://example.com/foo.pkl -> https://example.example/foo.pkl"
      )
  }

  @Test
  fun `http rewrites -- invalid rule`() {
    client.send(
      blankCreateEvaluatorRequest.copy(
        http =
          Http(
            caCertificates = null,
            proxy = null,
            rewrites = mapOf(URI("https://example.com") to URI("https://example.example/")),
          )
      )
    )
    val response = client.receive<CreateEvaluatorResponse>()
    assertThat(response.error)
      .contains("Rewrite rule must end with '/', but was 'https://example.com'")
  }

  private fun TestTransport.sendCreateEvaluatorRequest(
    requestId: Long = 123,
    resourceReaders: List<ResourceReaderSpec> = listOf(),
    moduleReaders: List<ModuleReaderSpec> = listOf(),
    modulePaths: List<Path> = listOf(),
    project: Project? = null,
    cacheDir: Path? = null,
    http: Http? = null,
  ): Long {
    val message =
      CreateEvaluatorRequest(
        123,
        listOf(".*"),
        listOf(".*"),
        moduleReaders,
        resourceReaders,
        modulePaths,
        mapOf(),
        mapOf(),
        null,
        null,
        cacheDir,
        null,
        project,
        http,
        null,
        null,
        null,
      )

    send(message)

    val response = receive<CreateEvaluatorResponse>()
    assertThat(response.requestId()).isEqualTo(requestId)
    assertThat(response.evaluatorId).isNotNull
    assertThat(response.error).isNull()

    return response.evaluatorId!!
  }
}
