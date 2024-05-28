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
package org.pkl.doc

import java.net.URI
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.pkl.commons.toPath
import org.pkl.commons.toUri
import org.pkl.core.Evaluator
import org.pkl.core.ModuleSchema
import org.pkl.core.ModuleSource.text
import org.pkl.core.ModuleSource.uri
import org.pkl.core.PClass
import org.pkl.core.TypeAlias

class DocScopeTest {
  companion object {
    private val evaluator = Evaluator.preconfigured()

    private val docPackageInfo =
      DocPackageInfo(
        "mypackage",
        "mypackage.",
        null,
        "1.2.3",
        "publisher",
        listOf("https://pkl-lang.org/"),
        "sourceCode".toUri(),
        "https://example.com/mypackage/blob/1.2.3%{path}#L%{line}-L%{endLine}",
        "issueTracker".toUri(),
        overview = "overview docs"
      )

    private val module: ModuleSchema by lazy {
      evaluator.evaluateSchema(
        text(
          """
          module mypackage.mymodule

          age: Int
          function sing(song) = "tra-la-la"

          class Person {
            name: String
            function call(number) = "calling"
          }

          typealias Email = String
          """
            .trimIndent()
        )
      )
    }

    private val moduleProperty: PClass.Property by lazy { module.moduleClass.properties["age"]!! }

    private val moduleMethod: PClass.Method by lazy { module.moduleClass.methods["sing"]!! }

    private val clazz: PClass by lazy { module.classes["Person"]!! }

    private val typeAlias: TypeAlias by lazy { module.typeAliases["Email"]!! }

    private val classProperty: PClass.Property by lazy { clazz.properties["name"]!! }

    private val classMethod: PClass.Method by lazy { clazz.methods["call"]!! }

    private val siteScope: SiteScope by lazy {
      SiteScope(
        listOf(DocPackage(docPackageInfo, mutableListOf(module))),
        mapOf(),
        { evaluator.evaluateSchema(uri(it)) },
        "/output/dir".toPath()
      )
    }

    private val packageScope: PackageScope by lazy { siteScope.getPackage("mypackage") }

    private val moduleScope: ModuleScope by lazy { packageScope.getModule("mypackage.mymodule") }

    private val modulePropertyScope: PropertyScope by lazy {
      PropertyScope(moduleProperty, moduleScope)
    }

    private val moduleMethodScope: MethodScope by lazy { MethodScope(moduleMethod, moduleScope) }

    private val classScope: ClassScope by lazy { ClassScope(clazz, moduleScope.url, moduleScope) }

    private val typeAliasScope: TypeAliasScope by lazy {
      TypeAliasScope(typeAlias, moduleScope.url, moduleScope)
    }

    private val classPropertyScope: PropertyScope by lazy {
      PropertyScope(classProperty, classScope)
    }

    private val classMethodScope: MethodScope by lazy { MethodScope(classMethod, classScope) }

    @JvmStatic
    private fun scopes(): Collection<DocScope> =
      listOf(
        moduleScope,
        modulePropertyScope,
        moduleMethodScope,
        classScope,
        typeAliasScope,
        classPropertyScope,
        classMethodScope
      )

    @JvmStatic
    @AfterAll
    fun afterAll() {
      evaluator.close()
    }
  }

  @ParameterizedTest
  @MethodSource("scopes")
  internal fun `resolve empty chain`(sourceScope: DocScope) {
    assertThat(sourceScope.resolveDocLink("")).isNull()
  }

  @ParameterizedTest
  @MethodSource("scopes")
  internal fun `resolve module property`(sourceScope: DocScope) {
    val targetScope = sourceScope.resolveDocLink("age")
    targetScope as PropertyScope

    assertThat(targetScope.property).isSameAs(moduleProperty)
    assertThat(targetScope.urlRelativeTo(moduleScope)).isEqualTo(URI("index.html#age"))
  }

  @ParameterizedTest
  @MethodSource("scopes")
  internal fun `resolve module method`(sourceScope: DocScope) {
    val targetScope = sourceScope.resolveDocLink("sing()")
    targetScope as MethodScope

    assertThat(targetScope.method).isSameAs(moduleMethod)
    assertThat(targetScope.urlRelativeTo(moduleScope)).isEqualTo(URI("index.html#sing()"))
  }

  @Test
  internal fun `resolve module method parameter`() {
    val targetScope = moduleMethodScope.resolveDocLink("song")
    targetScope as ParameterScope

    assertThat(targetScope.name).isEqualTo("song")
    assertThat(targetScope.urlRelativeTo(moduleScope)).isEqualTo(URI("index.html#sing().song"))
  }

  @ParameterizedTest
  @MethodSource("scopes")
  internal fun `resolve class`(sourceScope: DocScope) {
    val targetScope = sourceScope.resolveDocLink("Person")
    targetScope as ClassScope

    assertThat(targetScope.clazz).isSameAs(clazz)
    assertThat(targetScope.urlRelativeTo(moduleScope)).isEqualTo(URI("Person.html"))
  }

  @ParameterizedTest
  @MethodSource("scopes")
  internal fun `resolve type alias`(sourceScope: DocScope) {
    val targetScope = sourceScope.resolveDocLink("Email")
    targetScope as TypeAliasScope

    assertThat(targetScope.typeAlias).isSameAs(typeAlias)
    assertThat(targetScope.urlRelativeTo(moduleScope)).isEqualTo(URI("index.html#Email"))
  }

  @ParameterizedTest
  @MethodSource("scopes")
  internal fun `resolve class property`(sourceScope: DocScope) {
    val targetScope = sourceScope.resolveDocLink("Person.name")
    targetScope as PropertyScope

    assertThat(targetScope.property).isSameAs(classProperty)
    assertThat(targetScope.urlRelativeTo(moduleScope)).isEqualTo(URI("Person.html#name"))
  }

  @ParameterizedTest
  @MethodSource("scopes")
  internal fun `resolve class method`(sourceScope: DocScope) {
    val targetScope = sourceScope.resolveDocLink("Person.call()")
    targetScope as MethodScope

    assertThat(targetScope.method).isSameAs(classMethod)
    assertThat(targetScope.urlRelativeTo(moduleScope)).isEqualTo(URI("Person.html#call()"))
  }

  @Test
  internal fun `resolve class method parameter`() {
    val targetScope = classMethodScope.resolveDocLink("number")
    targetScope as ParameterScope

    assertThat(targetScope.name).isEqualTo("number")
    assertThat(targetScope.urlRelativeTo(moduleScope)).isEqualTo(URI("Person.html#call().number"))
  }

  @Test
  internal fun `site scope URL is below output dir even if directory does not exist`() {
    val outputDir = "/non/existing".toPath()
    val scope = SiteScope(listOf(), mapOf(), { evaluator.evaluateSchema(uri(it)) }, outputDir)

    // used to return `/non/index.html`
    assertThat(scope.url.toPath()).isEqualTo(Path.of("/non/existing/index.html").toAbsolutePath())
  }
}
