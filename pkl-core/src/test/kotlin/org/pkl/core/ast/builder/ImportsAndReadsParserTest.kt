package org.pkl.core.ast.builder

import org.pkl.core.SecurityManagers
import org.pkl.core.module.ModuleKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI

class ImportsAndReadsParserTest {
  @Test
  fun parse() {
    val moduleText = """
      amends "foo.pkl"
      
      import "bar.pkl"
      import "bazzy/buz.pkl"
      
      res1 = import("qux.pkl")
      res2 = import*("qux/*.pkl")
      
      class MyClass {
        res3 {
          res4 {
            res5 = read("/some/dir/chown.txt")
            res6 = read?("/some/dir/chowner.txt")
            res7 = read*("/some/dir/*.txt")
          }
        }
      }
    """.trimIndent()
    val moduleKey = ModuleKeys.synthetic(URI("repl:text"), moduleText)
    val imports = ImportsAndReadsParser.parse(moduleKey, moduleKey.resolve(SecurityManagers.defaultManager))
    assertThat(imports?.map { it.first }).hasSameElementsAs(listOf(
      "foo.pkl",
      "bar.pkl",
      "bazzy/buz.pkl",
      "qux.pkl",
      "qux/*.pkl",
      "/some/dir/chown.txt",
      "/some/dir/chowner.txt",
      "/some/dir/*.txt"
    ))
  }
}
