import java.net.URL
import org.gradle.util.GradleVersion
import groovy.json.JsonSlurper
import java.net.URI

@Suppress("unused")
class GradleVersionInfo(json: Map<String, Any>) {
  val version: String by json

  val gradleVersion: GradleVersion by lazy { GradleVersion.version(version) }

  val isReleaseVersion: Boolean by lazy {
    // for some reason, `gradleVersion == gradleVersion.baseVersion` is a compile error
    gradleVersion.version == gradleVersion.baseVersion.version
  }

  val buildTime: String by json

  val current: Boolean by json

  val snapshot: Boolean by json

  val nightly: Boolean by json

  val releaseNightly: Boolean by json

  val activeRc: Boolean by json

  val rcFor: String by json

  val milestoneFor: String by json

  val broken: Boolean by json

  val downloadUrl: String by json

  val checksumUrl: String by json

  val wrapperChecksumUrl: String by json

  companion object {
    private fun fetchAll(): List<GradleVersionInfo> = fetchMultiple("https://services.gradle.org/versions/all")

    fun fetchReleases(): List<GradleVersionInfo> = fetchAll().filter { it.isReleaseVersion }

    fun fetchCurrent(): GradleVersionInfo = fetchSingle("https://services.gradle.org/versions/current")

    fun fetchRc(): GradleVersionInfo? = fetchSingleOrNull("https://services.gradle.org/versions/release-candidate")

    fun fetchNightly(): GradleVersionInfo = fetchSingle("https://services.gradle.org/versions/nightly")

    private fun fetchSingle(url: String): GradleVersionInfo {
      @Suppress("UNCHECKED_CAST")
      return GradleVersionInfo(JsonSlurper().parse(URI(url).toURL()) as Map<String, Any>)
    }

    private fun fetchSingleOrNull(url: String): GradleVersionInfo? {
      @Suppress("UNCHECKED_CAST")
      val json = JsonSlurper().parse(URI(url).toURL()) as Map<String, Any>
      return if (json.isEmpty()) null else GradleVersionInfo(json)
    }

    private fun fetchMultiple(url: String): List<GradleVersionInfo> {
      @Suppress("UNCHECKED_CAST")
      return (JsonSlurper().parse(URI(url).toURL()) as List<Map<String, Any>>)
        .map { GradleVersionInfo(it) }
    }
  }
}
