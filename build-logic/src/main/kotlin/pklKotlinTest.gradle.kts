import com.adarshr.gradle.testlogger.theme.ThemeType
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.net.URI

plugins {
  kotlin("jvm")
  `jvm-test-suite`
  id("com.adarshr.test-logger")
}

val libs = the<LibrariesForLibs>()

dependencies {
  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitParams)
  testImplementation(libs.kotlinStdlib)

  testRuntimeOnly(libs.junitEngine)
}

tasks.withType<Test>().configureEach {
  val testTask = this
  forkEvery = 250
  maxParallelForks = 2

  useJUnitPlatform()

  // enable checking of stdlib return types
  systemProperty("org.pkl.testMode", "true")

  reports.named("html") {
    enabled = true
  }

  testLogging {
    exceptionFormat = TestExceptionFormat.FULL
  }

  addTestListener(object : TestListener {
    override fun beforeSuite(suite: TestDescriptor) {}
    override fun beforeTest(testDescriptor: TestDescriptor) {}
    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}

    // print report link at the end of the task, not just at the end of the build
    override fun afterSuite(descriptor: TestDescriptor, result: TestResult) {
      if (descriptor.parent != null) return // only interested in the overall result

      if (result.resultType == TestResult.ResultType.FAILURE) {
        println("\nThere were failing tests. See the report at: ${fixFileUri(testTask.reports.html.entryPoint.toURI())}")
      }
    }

    // makes links clickable on macOS
    private fun fixFileUri(uri: URI): URI {
      if ("file" == uri.scheme && !uri.schemeSpecificPart.startsWith("//")) {
        return URI.create("file://" + uri.schemeSpecificPart)
      }
      return uri
    }
  })
}

testlogger {
  theme = ThemeType.MOCHA_PARALLEL
  showPassed = true
  showFailed = true
  showSkipped = true
  showExceptions = false
  showStackTraces = false
  showStandardStreams = false
  slowThreshold = 45_000L
  isShowCauses = true
  isShowSimpleNames = true
}

testing {
  suites {
    @Suppress("UnstableApiUsage") val test by getting(JvmTestSuite::class) {
      useJUnitJupiter(libs.versions.junit)
      useKotlinTest(libs.versions.kotlin)
    }
  }
}
