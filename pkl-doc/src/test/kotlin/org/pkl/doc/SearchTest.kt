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
package org.pkl.doc

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.readString
import org.pkl.doc.CliDocGeneratorTest.Companion.package1InputModules
import org.pkl.doc.CliDocGeneratorTest.Companion.package1PackageModule

class SearchTest {
  companion object {
    private val tempFileSystem = lazy { Jimfs.newFileSystem(Configuration.unix()) }

    private val jsContext = lazy {
      // reuse CliDocGeneratorTest's input files (src/test/files/DocGeneratorTest/input)
      val packageModule: URI = package1PackageModule
      val inputModules: List<URI> = package1InputModules

      val pkldocDir = tempFileSystem.value.rootDirectories.first()

      CliDocGenerator(
          CliDocGeneratorOptions(
            base =
              CliBaseOptions(
                sourceModules = listOf(packageModule) + inputModules,
                settings = URI("pkl:settings"),
              ),
            outputDir = pkldocDir,
            true,
          )
        )
        .run()

      // `allowAllAccess(true)` disables host/guest language separation
      // (e.g., to pass `java.util.ArrayList` instances as arguments to JavaScript functions)
      Context.newBuilder("js").allowAllAccess(true).build().apply {
        eval(
          Source.create("js", pkldocDir.resolve("com.package1/1.2.3/search-index.js").readString())
        )
        eval(Source.create("js", pkldocDir.resolve("scripts/pkldoc.js").readString()))
        eval(Source.create("js", pkldocDir.resolve("scripts/search-worker.js").readString()))
      }
    }

    private val bindings: Value by lazy {
      jsContext.value.getBindings("js").apply { getMember("initSearchIndex").executeVoid() }
    }

    @AfterAll
    @JvmStatic
    @Suppress("unused")
    fun closeTempFileSystem() {
      if (tempFileSystem.isInitialized()) {
        tempFileSystem.value.close()
      }
    }

    @AfterAll
    @JvmStatic
    @Suppress("unused")
    fun closeJsContext() {
      if (jsContext.isInitialized()) {
        jsContext.value.close()
      }
    }
  }

  @Test
  fun toWordStarts() {
    val toWordStarts = bindings.getMember("toWordStarts")

    val wordStarts1 = toWordStarts.execute("modPropAnns".map { it.toString() })
    assertThat(wordStarts1.`as`(List::class.java))
      .containsExactly(0, 3, 3, 3, 7, 7, 7, 7, -1, -1, -1)

    val wordStarts2 = toWordStarts.execute("ModPropAnns".map { it.toString() })
    assertThat(wordStarts2.`as`(List::class.java))
      .containsExactly(0, 3, 3, 3, 7, 7, 7, 7, -1, -1, -1)

    // `PROP` is treated as one word, same as `Prop`
    // this means abbreviations are treated the same whether or not they are written in all caps
    // (e.g., `URL` vs. `Url`)
    val wordStarts3 = toWordStarts.execute("ModPROPAnns".map { it.toString() })
    assertThat(wordStarts3.`as`(List::class.java))
      .containsExactly(0, 3, 3, 3, 7, 7, 7, 7, -1, -1, -1)

    // number is treated as one word
    val wordStarts4 = toWordStarts.execute("Mod1234Anns".map { it.toString() })
    assertThat(wordStarts4.`as`(List::class.java))
      .containsExactly(0, 3, 3, 3, 7, 7, 7, 7, -1, -1, -1)

    val wordStarts5 = toWordStarts.execute("Mod1234anns".map { it.toString() })
    assertThat(wordStarts5.`as`(List::class.java))
      .containsExactly(0, 3, 3, 3, 7, 7, 7, 7, -1, -1, -1)

    val wordStarts6 = toWordStarts.execute("123PropAnns".map { it.toString() })
    assertThat(wordStarts6.`as`(List::class.java))
      .containsExactly(0, 3, 3, 3, 7, 7, 7, 7, -1, -1, -1)
  }

  @Test
  fun `search with full name`() {
    checkSearchResults("modulePropertyAnnotations", setOf("com.package1.modulePropertyAnnotations"))
  }

  @Test
  fun `search with full name without capitalization`() {
    checkSearchResults("modulepropertyannotations", setOf("com.package1.modulePropertyAnnotations"))
  }

  @Test
  fun `characters are normalized`() {
    checkSearchResults("modulépropertyannotåtions", setOf("com.package1.modulePropertyAnnotations"))
  }

  @Test
  fun `search with start of name`() {
    checkSearchResults(
      "modulepr",
      setOf(
        "com.package1.modulePropertyAnnotations",
        "com.package1.modulePropertyCommentInheritance",
        "com.package1.modulePropertyComments",
        "com.package1.modulePropertyModifiers",
        "com.package1.modulePropertyTypeAnnotations",
        "com.package1.modulePropertyTypeReferences",
      ),
    )
  }

  @Test
  fun `search with end of name`() {
    checkSearchResults(
      "pereferences",
      setOf(
        "TypeReferences",
        "com.package1.classMethodTypeReferences",
        "com.package1.classPropertyTypeReferences",
        "com.package1.moduleMethodTypeReferences",
        "com.package1.modulePropertyTypeReferences",
      ),
    )
  }

  @Test
  fun `search with middle of name`() {
    checkSearchResults(
      "with",
      setOf(
        "prpertyWithExpandableComment",
        "AnnotatedClssWithExpandableComment",
        "ClassWithAnnotatedProperty",
        "mthodWithExpandableComment",
      ),
    )
  }

  @Test
  fun `search with all word starts`() {
    checkSearchResults("mpa", setOf("com.package1.modulePropertyAnnotations"))
  }

  @Test
  fun `search for prefix of word starts`() {
    checkSearchResults(
      "mp",
      setOf(
        "myProperty",
        "com.package1.modulePropertyComments",
        "com.package1.modulePropertyModifiers",
        "com.package1.modulePropertyAnnotations",
        "com.package1.modulePropertyTypeReferences",
        "com.package1.modulePropertyTypeAnnotations",
        "com.package1.modulePropertyCommentInheritance",
        "com.package1.docExampleSubject1",
        "com.package1.docExampleSubject2",
      ),
    )
  }

  @Test
  fun `search with postfix of word starts`() {
    checkSearchResults(
      "ci",
      setOf(
        "com.package1.classInheritance",
        "city", // disregard
        "com.package1.moduleMethodCommentInheritance",
        "com.package1.modulePropertyCommentInheritance",
      ),
    )
  }

  @Test
  fun `search with numbers`() {
    checkSearchResults("property7", setOf("property7"))
    checkSearchResults("prop7", setOf("property7"))
    checkSearchResults("p7", setOf("property7"))
  }

  @Test
  fun `cannot skip middle characters (match must be contiguous)`() {
    checkSearchResults("method7", setOf("method7"))
    checkSearchResults("methd7", setOf())
  }

  @Test
  fun `cannot skip middle word starts (match must be contiguous)`() {
    checkSearchResults("pwec", setOf("prpertyWithExpandableComment"))
    checkSearchResults("pwc", setOf())
  }

  @Test
  fun `exact match of last module name component is considered exact match`() {
    checkExactSearchResults("modulePropertyComments", setOf("com.package1.modulePropertyComments"))
  }

  @Test
  fun `exact word start match of last module name component is considered exact match`() {
    checkExactSearchResults("mpc", setOf("com.package1.modulePropertyComments"))
  }

  private fun checkSearchResults(inputValue: String, expected: Set<String>) {
    val query = bindings.getMember("parseSearchInput").execute(inputValue)
    val results = bindings.getMember("runSearch").execute(query, null, null)
    val actual =
      getResultsForCategory(results, "exactMatches") +
        getResultsForCategory(results, "classMatches") +
        getResultsForCategory(results, "moduleMatches") +
        getResultsForCategory(results, "otherMatches")

    assertThat(actual).isEqualTo(expected)
  }

  private fun checkExactSearchResults(inputValue: String, expected: Set<String>) {
    val query = bindings.getMember("parseSearchInput").execute(inputValue)
    val results = bindings.getMember("runSearch").execute(query, null, null)
    val actual = getResultsForCategory(results, "exactMatches")

    assertThat(actual).isEqualTo(expected)
  }

  private fun getResultsForCategory(results: Value, category: String): Set<String> {
    val categoryResults = results.getMember(category)

    return (0 until categoryResults.arraySize)
      .map { categoryResults.getArrayElement(it).getMember("name").`as`(String::class.java) }
      .toSet()
  }
}
