import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty

/**
 * Builds a self-contained Pkl CLI Jar that is directly executable on *nix
 * and executable with `java -jar` on Windows.
 *
 * For direct execution, the `java` command must be on the PATH.
 *
 * https://skife.org/java/unix/2011/06/20/really_executable_jars.html
 */
open class ExecutableJar : DefaultTask() {
  @get:InputFile
  val inJar: RegularFileProperty = project.objects.fileProperty()

  @get:OutputFile
  val outJar: RegularFileProperty = project.objects.fileProperty()

  @get:Input
  val jvmArgs: ListProperty<String> = project.objects.listProperty()

  @TaskAction
  fun buildJar() {
    val inFile = inJar.get().asFile
    val outFile = outJar.get().asFile
    val escapedJvmArgs = jvmArgs.get().joinToString(separator = " ") { "\"$it\"" }

    val startScript = """
      #!/bin/sh
      exec java $escapedJvmArgs -jar $0 "$@"
    """.trim().trimMargin() + "\n\n\n"

    outFile.outputStream().use { outStream ->
      startScript.byteInputStream().use { it.copyTo(outStream) }
      inFile.inputStream().use { it.copyTo(outStream) }
    }

    // chmod a+x
    outFile.setExecutable(true, false)
  }
}
