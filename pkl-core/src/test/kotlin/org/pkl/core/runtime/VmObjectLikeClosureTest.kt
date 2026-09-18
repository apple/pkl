/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.runtime

import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.Loggers
import org.pkl.core.SecurityManagers
import org.pkl.core.StackFrameTransformers
import org.pkl.core.evaluatorSettings.TraceMode
import org.pkl.core.http.HttpClient
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.module.ModuleKeys

// Most capture behavior is already tested by way of LanguageSnippetTests.
// These are just some smoke tests that we are indeed avoiding closures in some cases.
class VmObjectLikeClosureTest {
  companion object {
    private fun makeObject(pklCode: String): VmObjectLike {
      val securityManager =
        SecurityManagers.standard(
          SecurityManagers.defaultAllowedModules,
          SecurityManagers.defaultAllowedResources,
          SecurityManagers.defaultTrustLevels,
          null,
        )
      var ret: VmObjectLike? = null
      val moduleKey = ModuleKeys.synthetic(URI("repl:text"), pklCode)
      VmUtils.createContext {
          val vmContext = VmContext.get(null)
          vmContext.initialize(
            VmContext.Holder(
              StackFrameTransformers.defaultTransformer,
              securityManager,
              HttpClient.dummyClient(),
              ModuleResolver(listOf(ModuleKeyFactories.standardLibrary)),
              ResourceManager(securityManager, listOf()),
              Loggers.noop(),
              mapOf(),
              mapOf(),
              null,
              null,
              null,
              null,
              TraceMode.COMPACT,
              false,
            )
          )
          val module = VmLanguage.get(null).loadModule(moduleKey)
          ret = VmUtils.readMember(module, Identifier.get("the member")) as VmObjectLike
        }
        .close()
      return ret!!
    }
  }

  @Test
  fun `function does not capture if reference does not escape`() {
    val function =
      makeObject(
        """
        `the member` = (x) -> x + 1
        """
          .trimIndent()
      )
    assertThat(function.enclosingFrame).isNull()
  }

  @Test
  fun `function captures closure if reference escapes`() {
    val function =
      makeObject(
        """
        y = 1

        `the member` = (x) -> x + y
        """
          .trimIndent()
      )
    assertThat(function.enclosingFrame).isNotNull
  }

  @Test
  fun `function captures if uses type from enclosing module`() {
    val function =
      makeObject(
        """
        class Foo {}

        `the member` = () -> new Foo {}
        """
          .trimIndent()
      )
    assertThat(function.enclosingFrame).isNotNull
  }

  @Test
  fun `function does not capture if using type from base module`() {
    val function =
      makeObject(
        """
        `the member` = () -> new Listing {}
        """
          .trimIndent()
      )
    assertThat(function.enclosingFrame).isNull()
  }

  @Test
  fun `object does not capture if reference does not escape`() {
    val obj =
      makeObject(
        """
        `the member` = new { res = 1 }
        """
          .trimIndent()
      )
    assertThat(obj.enclosingFrame).isNull()
  }

  @Test
  fun `object captures closure if reference escapes`() {
    val obj =
      makeObject(
        """
        x = 1
        `the member` = new { res = x }
        """
          .trimIndent()
      )
    assertThat(obj.enclosingFrame).isNotNull
  }

  @Test
  fun `object captures if uses type from enclosing module`() {
    val obj =
      makeObject(
        """
        class Foo {}

        `the member` = new Foo {}
        """
          .trimIndent()
      )
    assertThat(obj.enclosingFrame).isNotNull
  }

  @Test
  fun `object does not capture if using type from base module`() {
    val obj =
      makeObject(
        """
        `the member` = new Listing {}
        """
          .trimIndent()
      )
    assertThat(obj.enclosingFrame).isNull()
  }

  @Test
  fun `object does not capture if references come from an eager scope`() {
    val obj =
      makeObject(
        """
        elems = List(1, 2, 3)

        `the member` = new {
          for (x in elems) {
            x + 1
          }
        }
        """
          .trimIndent()
      )
    assertThat(obj.enclosingFrame).isNull()
  }

  @Test
  fun `object does capture if parent is a lambda`() {
    val obj =
      makeObject(
        """
        local func = () -> new Listing {}

        local elems = List(1, 2, 3, 4)

        `the member` = (func) {
          for (x in elems) {
            x + 1
          }
        }
        """
          .trimIndent()
      )
    assertThat(obj.enclosingFrame).isNotNull
  }
}
