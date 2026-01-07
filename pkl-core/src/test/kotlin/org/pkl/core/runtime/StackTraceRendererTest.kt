/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.core.*
import org.pkl.core.util.AnsiStringBuilder

class StackTraceRendererTest {
  companion object {
    private val evaluator = Evaluator.preconfigured()

    @JvmStatic
    @AfterAll
    @Suppress("unused")
    fun afterAll() {
      evaluator.close()
    }
  }

  @Test
  fun `stringy self-reference`() {
    val message =
      assertThrows<PklException> {
          evaluator.evaluate(
            ModuleSource.text(
              """
          self: String = "Strings; if they were lazy, you could tie the knot on \(self.take(7))"
          """
                .trimIndent()
            )
          )
        }
        .message!!
    assertThat(message)
      .contains("A stack overflow occurred.")
      .containsPattern(
        """
┌─ \d* repetitions of:
│ 1 | self: String = "Strings; if they were lazy, you could tie the knot on \\\(self.take\(7\)\)"
│                                                                             ^^^^
   """
          .trim()
      )
  }

  @Test
  fun `cyclic property references`() {
    val message =
      assertThrows<PklException> {
          evaluator.evaluate(
            ModuleSource.text(
              """
              foo: String = "FOO:" + bar
              bar: String = "BAR:" + baz
              baz: String = "BAZ:" + qux
              qux: String = "QUX:" + foo
            """
                .trimIndent()
            )
          )
        }
        .message!!
    assertThat(message)
      .contains("A stack overflow occurred.")
      .containsPattern(
        """
┌─ \d+ repetitions of:
│ 4 | qux: String = "QUX:" + foo
│                            ^^^
│ at text#qux (repl:text)
│ 
│ 3 | baz: String = "BAZ:" + qux
│                            ^^^
│ at text#baz (repl:text)
│ 
│ 2 | bar: String = "BAR:" + baz
│                            ^^^
│ at text#bar (repl:text)
│ 
│ 1 | foo: String = "FOO:" + bar
│                            ^^^
│ at text#foo (repl:text)
└─        
      """
          .trim()
      )
  }

  @Test
  @Suppress("RegExpRepeatedSpace")
  fun `reduce stack overflow from actual Pkl code`() {
    val pklCode =
      """
        function suffix(n: UInt): UInt =
          if (n == 0)
            0
          else
            suffix(n - 1)

        function loopBody4(n: UInt): UInt =
          if (n == 0)
            loop()
          else
            loopBody1(n - 1)

        function loopBody3(n: UInt) = loopBody4(n)
        function loopBody2(n: UInt) = loopBody3(n)
        function loopBody1(n: UInt) = loopBody2(n)

        function loop(): UInt =
          if (suffix(100) > 0)
            1
          else
            loopBody1(5)

        function prefix(n: UInt): UInt =
          if (n == 0)
            loop()
          else
            prefix(n - 1)

        result = prefix(13)
      """
        .trimIndent()
    val message =
      assertThrows<PklException> { evaluator.evaluate(ModuleSource.text(pklCode)) }.message!!

    if (message.contains("5 | suffix")) {
      assertThat(message).containsPattern("repetitions of:\n│ 5 | suffix(n - 1)")
    }

    assertThat(message)
      .contains("A stack overflow occurred.")
      .containsPattern("┌─ \\d+ repetitions of:\n│ \n│ 9 | loop\\(\\)")
      .containsPattern("│ ┌─ 5 repetitions of:\n│  │ \n│  │ 11 | loopBody1\\(n - 1\\)")
      .containsPattern("┌─ 13 repetitions of:\n│ \n│ 27 | prefix\\(n - 1\\)")
  }

  @Test
  fun `compression preserves prefix and suffix and counts loop correctly`() {
    val prefixLength = 5
    val loopLength = 30
    val suffixLength = 10
    val repetitions = 4

    val prefix = List(prefixLength) { createFrame("Prefix", prefixLength - it) }
    val loop = List(loopLength) { createFrame("Loop", loopLength - it) }
    val suffix = List(suffixLength) { createFrame("Suffix", suffixLength - it) }

    val frames = buildList {
      addAll(suffix)
      repeat(repetitions) { addAll(loop) }
      addAll(prefix)
    }
    val compressedFrames = StackTraceRenderer.compressFrames(frames)

    assertThat(compressedFrames.size).isEqualTo(suffix.size + 1 + prefix.size)

    val compressedLoop = compressedFrames[suffix.size] as? StackTraceRenderer.StackFrameLoop
    assertThat(compressedFrames.take(suffix.size)).isEqualTo(suffix)
    assertThat(compressedLoop?.frames).isEqualTo(loop)
    assertThat(compressedLoop?.count).isEqualTo(repetitions)
    assertThat(compressedFrames.drop(suffix.size + 1)).isEqualTo(prefix)
  }

  // TODO: fix these false positives in the loop search algorithm.
  @Test
  fun `cycles of length 1 don't get rendered as a loop`() {
    val renderer = StackTraceRenderer(StackFrameTransformers.defaultTransformer)
    val loopFrames = buildList {
      add(createFrame("foo", 1))
      add(createFrame("foo", 2))
      add(createFrame("foo", 3))
    }
    val loop = StackTraceRenderer.StackFrameLoop(loopFrames, 1)
    val frames = listOf(createFrame("bar", 1), createFrame("baz", 2), loop)
    val formatter = AnsiStringBuilder(false)
    renderer.doRender(frames, null, null, formatter, "", true)
    val renderedFrames = formatter.toString()
    assertThat(renderedFrames)
      .isEqualTo(
        """
      1 | foo
          ^
      at <unknown> (file:bar)

      2 | foo
          ^
      at <unknown> (file:baz)

      1 | foo
          ^
      at <unknown> (file:foo)

      2 | foo
          ^
      at <unknown> (file:foo)

      3 | foo
          ^
      at <unknown> (file:foo)

    """
          .trimIndent()
      )
  }

  private fun createFrame(name: String, id: Int): StackFrame {
    return StackFrame("file:$name", null, listOf("foo"), id, 1, id, 1)
  }
}
