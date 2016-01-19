package org.pkl.core

import org.pkl.core.project.Project
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path
import java.time.Duration

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
    val e1 = assertThrows<IllegalStateException> {
      EvaluatorBuilder.unconfigured()
        .setStackFrameTransformer(StackFrameTransformers.empty)
        .build()
    }
    assertThat(e1).hasMessage("No security manager set.")
  }

  @Test
  fun `enforces that stack frame transformer is set`() {
    val e1 = assertThrows<IllegalStateException> {
      EvaluatorBuilder.unconfigured()
        .setSecurityManager(SecurityManagers.defaultManager)
        .build()
    }
    assertThat(e1).hasMessage("No stack frame transformer set.")
  }
  
  @Test
  fun `sets evaluator settings from project`() {
    val projectPath = Path.of(javaClass.getResource("project/project1/PklProject")!!.toURI())
    val project = Project.loadFromPath(
      projectPath,
      SecurityManagers.defaultManager,
      null
    )
    val projectDir = Path.of(javaClass.getResource("project/project1/PklProject")!!.toURI()).parent
    val builder = EvaluatorBuilder
      .unconfigured()
      .applyFromProject(project)
    assertThat(builder.allowedResources.map { it.pattern() })
      .isEqualTo(listOf("foo:", "bar:"))
    assertThat(builder.allowedModules.map { it.pattern() })
      .isEqualTo(listOf("baz:", "biz:"))
    assertThat(builder.externalProperties).isEqualTo(mapOf("one" to "1"))
    assertThat(builder.environmentVariables).isEqualTo(mapOf("two" to "2"))
    assertThat(builder.moduleCacheDir).isEqualTo(projectDir.resolve("my-cache-dir/"))
    assertThat(builder.rootDir).isEqualTo(projectDir.resolve("my-root-dir/"))
    assertThat(builder.timeout).isEqualTo(Duration.ofMinutes(5L))
  }
}
