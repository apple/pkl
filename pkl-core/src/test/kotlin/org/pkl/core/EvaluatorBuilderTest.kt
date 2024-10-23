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
package org.pkl.core

import java.nio.file.Path
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.core.project.Project

class EvaluatorBuilderTest {
  @Test
  fun `preconfigured builder sets process env vars`() {
    val builder = EvaluatorBuilder.preconfigured()
    assertThat(builder.environmentVariables).isEqualTo(System.getenv())
  }

  @Test
  fun `preconfigured builder sets system properties`() {
    val builder = EvaluatorBuilder.preconfigured()
    assertThat(builder.externalProperties).isEqualTo(System.getProperties())
  }

  @Test
  fun `unconfigured builder does not set process env vars`() {
    val builder = EvaluatorBuilder.unconfigured()
    assertThat(builder.environmentVariables).isEmpty()
  }

  @Test
  fun `unconfigured builder does not set system properties`() {
    val builder = EvaluatorBuilder.unconfigured()
    assertThat(builder.externalProperties).isEmpty()
  }

  @Test
  fun `enforces that security manager is set`() {
    val e1 =
      assertThrows<IllegalStateException> {
        EvaluatorBuilder.unconfigured()
          .setStackFrameTransformer(StackFrameTransformers.empty)
          .build()
      }
    assertThat(e1).hasMessage("No security manager set.")
  }

  @Test
  fun `enforces that stack frame transformer is set`() {
    val e1 =
      assertThrows<IllegalStateException> {
        EvaluatorBuilder.unconfigured().setSecurityManager(SecurityManagers.defaultManager).build()
      }
    assertThat(e1).hasMessage("No stack frame transformer set.")
  }

  @Test
  fun `sets evaluator settings from project`() {
    val projectPath = Path.of(javaClass.getResource("project/project1/PklProject")!!.toURI())
    val project = Project.loadFromPath(projectPath, SecurityManagers.defaultManager, null)
    val projectDir = Path.of(javaClass.getResource("project/project1/PklProject")!!.toURI()).parent
    val builder = EvaluatorBuilder.unconfigured().applyFromProject(project)
    assertThat(builder.allowedResources.map { it.pattern() }).isEqualTo(listOf("foo:", "bar:"))
    assertThat(builder.allowedModules.map { it.pattern() }).isEqualTo(listOf("baz:", "biz:"))
    assertThat(builder.externalProperties).isEqualTo(mapOf("one" to "1"))
    assertThat(builder.environmentVariables).isEqualTo(mapOf("two" to "2"))
    assertThat(builder.moduleCacheDir).isEqualTo(projectDir.resolve("my-cache-dir/"))
    assertThat(builder.rootDir).isEqualTo(projectDir.resolve("my-root-dir/"))
    assertThat(builder.timeout).isEqualTo(Duration.ofMinutes(5L))
  }
}
