package org.pkl.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.core.project.Project
import java.nio.file.Path
import java.time.Duration
import java.util.regex.Pattern

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
  fun `unconfigured builder defaults to StackFrameTransformers_empty`() {
    val builder = EvaluatorBuilder.unconfigured()
    assertThat(builder.stackFrameTransformer).isSameAs(StackFrameTransformers.empty)
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

  @Test
  fun `does not change builder state when build() is called`() {
    val builder = EvaluatorBuilder.unconfigured()
      .addAllowedModule(Pattern.compile("module"))
    assertThat(builder.securityManager).isNull()
    builder.build()
    assertThat(builder.securityManager).isNull()
  }

  @Test
  fun `requires that allowedModules is non-empty (otherwise every evaluate() would fail)`() {
    val e1 = assertThrows<IllegalStateException> {
      EvaluatorBuilder.unconfigured().build()
    }
    assertThat(e1).hasMessage("allowedModules cannot be empty")
  }

  @Test
  fun `requires that no security manager is set if allowedModules is set`() {
    val e1 = assertThrows<IllegalStateException> {
      EvaluatorBuilder.unconfigured()
        .setSecurityManager(SecurityManagers.defaultManager)
        .addAllowedModule(Pattern.compile(""))
    }
    assertThat(e1)
      .hasMessageContaining("addAllowedModule")
      .hasMessageContaining("setSecurityManager")

    val e2 = assertThrows<IllegalStateException> {
      EvaluatorBuilder.unconfigured()
        .setSecurityManager(SecurityManagers.defaultManager)
        .setAllowedModules(listOf(Pattern.compile("")))
    }
    assertThat(e2)
      .hasMessageContaining("setAllowedModules")
      .hasMessageContaining("setSecurityManager")
  }

  @Test
  fun `requires that no security manager is set if allowedResources is set`() {
    val e1 = assertThrows<IllegalStateException> {
      EvaluatorBuilder.unconfigured()
        .setSecurityManager(SecurityManagers.defaultManager)
        .addAllowedResource(Pattern.compile(""))
    }
    assertThat(e1)
      .hasMessageContaining("addAllowedResource")
      .hasMessageContaining("setSecurityManager")

    val e2 = assertThrows<IllegalStateException> {
      EvaluatorBuilder.unconfigured()
        .setSecurityManager(SecurityManagers.defaultManager)
        .setAllowedResources(listOf(Pattern.compile("")))
    }
    assertThat(e2)
      .hasMessageContaining("setAllowedResources")
      .hasMessageContaining("setSecurityManager")
  }

  @Test
  fun `requires that no security manager is set if rootDir is set`() {
    val e1 = assertThrows<IllegalStateException> {
      EvaluatorBuilder.unconfigured()
        .setSecurityManager(SecurityManagers.defaultManager)
        .setRootDir(Path.of(""))
    }
    assertThat(e1)
      .hasMessageContaining("setRootDir")
      .hasMessageContaining("setSecurityManager")
  }

  @Test
  fun `requires that no security manager is set if trustLevels is set`() {
    val e1 = assertThrows<IllegalStateException> {
      EvaluatorBuilder.unconfigured()
        .setSecurityManager(SecurityManagers.defaultManager)
        .setTrustLevels(SecurityManagers.defaultTrustLevels)
    }
    assertThat(e1)
      .hasMessageContaining("setTrustLevels")
      .hasMessageContaining("setSecurityManager")
  }
}
