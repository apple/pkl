package org.pkl.core.settings

import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.createParentDirectories
import org.pkl.commons.writeString
import org.pkl.core.Evaluator
import org.pkl.core.ModuleSource
import org.pkl.core.PObject
import org.pkl.core.httpsettings.PklHttpSettings
import org.pkl.core.settings.PklSettings.Editor

class PklSettingsTest {
  @Test
  fun `load user settings`(@TempDir tempDir: Path) {
    val settingsPath = tempDir.resolve("settings.pkl")
    settingsPath.createParentDirectories()
    settingsPath.writeString(
      """
      amends "pkl:settings"
      editor = Sublime
      """.trimIndent()
    )

    val settings = PklSettings.loadFromPklHomeDir(tempDir)
    assertThat(settings).isEqualTo(PklSettings(Editor.SUBLIME, PklHttpSettings.DEFAULT))
  }

  @Test
  fun `load settings from path`(@TempDir tempDir: Path) {
    val settingsPath = tempDir.resolve("my-settings.pkl")
    settingsPath.writeString(
      """
      amends "pkl:settings"
      editor = Idea
      """.trimIndent()
    )

    val settings = PklSettings.load(ModuleSource.path(settingsPath))
    assertThat(settings).isEqualTo(PklSettings(Editor.IDEA, PklHttpSettings.DEFAULT))
  }

  @Test
  fun `predefined editors`() {
    val evaluator = Evaluator.preconfigured()
    val module = evaluator.evaluate(
      ModuleSource.text(
        """
        import "pkl:settings"
  
        system = settings.System
        idea = settings.Idea
        textMate = settings.TextMate
        sublime = settings.Sublime
        atom = settings.Atom
        vsCode = settings.VsCode
        """.trimIndent()
      )
    )

    checkEquals(Editor.SYSTEM, module.getProperty("system") as PObject)
    checkEquals(Editor.IDEA, module.getProperty("idea") as PObject)
    checkEquals(Editor.TEXT_MATE, module.getProperty("textMate") as PObject)
    checkEquals(Editor.SUBLIME, module.getProperty("sublime") as PObject)
    checkEquals(Editor.ATOM, module.getProperty("atom") as PObject)
    checkEquals(Editor.VS_CODE, module.getProperty("vsCode") as PObject)
  }

  private fun checkEquals(expected: Editor, actual: PObject) {
    assertThat(actual.getProperty("urlScheme") as String).isEqualTo(expected.urlScheme)
  }
}
