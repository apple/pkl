import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.JvmLibrary
import org.gradle.kotlin.dsl.property
import org.gradle.language.base.artifact.SourcesArtifact

open class ResolveSourcesJars : DefaultTask() {
  @get:InputFiles
  val configuration: Property<Configuration> = project.objects.property()

  @get:OutputDirectory
  val outputDir: DirectoryProperty = project.objects.directoryProperty()

  @TaskAction
  @Suppress("UnstableApiUsage", "unused")
  fun resolve() {
    val componentIds = configuration.get().incoming.resolutionResult.allDependencies.map {
      (it as ResolvedDependencyResult).selected.id
    }

    val resolutionResult = project.dependencies.createArtifactResolutionQuery()
      .forComponents(componentIds)
      .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java)
      .execute()

    val resolvedJars = resolutionResult.resolvedComponents
      .flatMap { it.getArtifacts(SourcesArtifact::class.java) }
      .map { (it as ResolvedArtifactResult).file }

    // copying to an output dir because I don't know how else to describe task outputs
    project.sync {
      from(resolvedJars)
      into(outputDir)
    }
  }
}
