package org.pkl.gradle

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class TestsTest : AbstractTest() {

  @Test
  fun `facts pass`() {
    writeBuildFile()

    writePklFile()

    val res = runTask("evalTest")
    assertThat(res.output).contains("should pass ✅")
  }

  @Test
  fun `facts fail`() {
    writeBuildFile()

    writePklFile(additionalFacts = """
      ["should fail"] {
        1 == 3
        "foo" == "bar"
      }
    """.trimIndent())

    val res = runTask("evalTest", expectFailure = true)
    assertThat(res.output).contains("should fail ❌")
    assertThat(res.output).contains("1 == 3 ❌")
    assertThat(res.output).contains(""""foo" == "bar" ❌""")
  }

  @Test
  fun error() {
    writeBuildFile()

    writePklFile(additionalFacts = """
      ["error"] {
        throw("exception")
      }
    """.trimIndent())

    val output = runTask("evalTest", expectFailure = true).output.stripFilesAndLines()

    assertThat(output.trimStart()).startsWith("""
      > Task :evalTest FAILED
      module test (file:///file, line x)
        test ❌
          Error:
              –– Pkl Error ––
              exception
              
              9 | throw("exception")
                  ^^^^^^^^^^^^^^^^^^
              at test#facts["error"][#1] (file:///file, line x)
              
              3 | facts {
                  ^^^^^^^
              at test#facts (file:///file, line x)
    """.trimIndent())
  }

  @Test
  fun `full example`() {
    writePklFile(contents = bigTest)
    writeFile("test.pkl-expected.pcf", bigTestExpected)

    writeBuildFile()

    val output = runTask("evalTest", expectFailure = true).output.stripFilesAndLines()

    assertThat(output.trimStart()).contains("""
      module test (file:///file, line x)
        sum numbers ✅
        divide numbers ✅
        fail ❌
          4 == 9 ❌ (file:///file, line x)
          "foo" == "bar" ❌ (file:///file, line x)
        user 0 ✅
        user 1 ❌
          (file:///file, line x)
          Expected: (file:///file, line x)
          new {
            name = "Pigeon"
            age = 40
          }
          Actual: (file:///file, line x)
          new {
            name = "Pigeon"
            age = 41
          }
        user 1 ❌
          (file:///file, line x)
          Expected: (file:///file, line x)
          new {
            name = "Parrot"
            age = 35
          }
          Actual: (file:///file, line x)
          new {
            name = "Welma"
            age = 35
          }
    """.trimIndent())
  }

  @Test
  fun `overwrite expected examples`() {
    writePklFile(additionalExamples = """
      ["user 0"] {
        new {
          name = "Cool"
          age = 11
        }
      }
      ["user 1"] {
        new {
          name = "Pigeon"
          age = 41
        }
        new {
          name = "Welma"
          age = 35
        }
      }
    """.trimIndent())
    writeFile("test.pkl-expected.pcf", bigTestExpected)

    writeBuildFile("overwrite = true")

    val output = runTask("evalTest").output

    assertThat(output).contains("user 0 ✍️")
    assertThat(output).contains("user 1 ✍️")
  }

  @Test
  fun `JUnit reports`() {
    val pklFile = writePklFile(contents = bigTest)
    writeFile("test.pkl-expected.pcf", bigTestExpected)

    writeBuildFile("junitReportsDir = file('${pklFile.parent}/build')")

    runTask("evalTest", expectFailure = true)

    val outputFile = testProjectDir.resolve("build/test.xml")
    val report = outputFile.readText().stripFilesAndLines()

    assertThat(report).isEqualTo("""
      <?xml version="1.0" encoding="UTF-8"?>
      <testsuite name="test" tests="6" failures="4">
          <testcase classname="test" name="sum numbers"></testcase>
          <testcase classname="test" name="divide numbers"></testcase>
          <testcase classname="test" name="fail">
              <failure message="Fact Failure">4 == 9 ❌ (file:///file, line x)</failure>
              <failure message="Fact Failure">&quot;foo&quot; == &quot;bar&quot; ❌ (file:///file, line x)</failure>
          </testcase>
          <testcase classname="test" name="user 0"></testcase>
          <testcase classname="test" name="user 1">
              <failure message="Example Failure">(file:///file, line x)
      Expected: (file:///file, line x)
      new {
        name = &quot;Pigeon&quot;
        age = 40
      }
      Actual: (file:///file, line x)
      new {
        name = &quot;Pigeon&quot;
        age = 41
      }</failure>
          </testcase>
          <testcase classname="test" name="user 1">
              <failure message="Example Failure">(file:///file, line x)
      Expected: (file:///file, line x)
      new {
        name = &quot;Parrot&quot;
        age = 35
      }
      Actual: (file:///file, line x)
      new {
        name = &quot;Welma&quot;
        age = 35
      }</failure>
          </testcase>
          <system-err><![CDATA[8 = 8
      ]]></system-err>
      </testsuite>
      
    """.trimIndent())
  }

  private val bigTest = """
    amends "pkl:test"

    local function sum(a, b) = a + b
    
    facts {
      ["sum numbers"] {
        sum(3, 5) == trace(8)
        sum(3, 0) == 3
      }
      ["divide numbers"] {
        (8 / 4) == 2
        (12 / 2) == 6
      }
      ["fail"] {
        4 == 9
        "foo" == "bar"
      }
    }
    
    examples {
      ["user 0"] {
        new {
          name = "Cool"
          age = 11
        }
      }
      ["user 1"] {
        new {
          name = "Pigeon"
          age = 41
        }
        new {
          name = "Welma"
          age = 35
        }
      }
    }
  """.trimIndent()

  private val bigTestExpected = """
    examples {
      ["user 0"] {
        new {
          name = "Cool"
          age = 11
        }
      }
      ["user 1"] {
        new {
          name = "Pigeon"
          age = 40
        }
        new {
          name = "Parrot"
          age = 35
        }
      }
    }
  """.trimIndent()

  private fun writeBuildFile(additionalContents: String = "") {
    writeFile(
      "build.gradle", """
      plugins {
        id "com.apple.pkl"
      }

      pkl {
        tests {
          evalTest {
            sourceModules = ["test.pkl"]
            settingsModule = "pkl:settings"
            $additionalContents
          }
        }
      }
    """
    )
  }

  private fun writePklFile(
    additionalFacts: String = "",
    additionalExamples: String = "",
    contents: String = """
    amends "pkl:test"
    
    facts {
      ["should pass"] {
        1 == 1
        10 == 10
      }
      $additionalFacts
    }
    
    examples {
      $additionalExamples
    }
    """
  ): Path {
    return writeFile("test.pkl", contents)
  }

  private fun String.stripFilesAndLines(): String =
    replace(Regex("""\(file:///.*, line \d+\)"""), "(file:///file, line x)")
}
