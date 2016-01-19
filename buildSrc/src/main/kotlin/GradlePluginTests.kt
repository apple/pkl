import org.gradle.util.GradleVersion

open class GradlePluginTests {
  lateinit var minGradleVersion: GradleVersion
  lateinit var maxGradleVersion: GradleVersion
  var skippedGradleVersions: List<GradleVersion> = listOf()
}