import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.mapProperty

open class MergeSourcesJars : DefaultTask() {
  @get:InputFiles
  val inputJars: ConfigurableFileCollection = project.objects.fileCollection()

  @get:InputFiles
  val mergedBinaryJars: ConfigurableFileCollection = project.objects.fileCollection()

  @get:Input
  val relocatedPackages: MapProperty<String, String> = project.objects.mapProperty()

  @get:Input
  var sourceFileExtensions: ListProperty<String> = project.objects.listProperty<String>()
    .convention(listOf(".java", ".kt"))

  @get:OutputFile
  val outputJar: RegularFileProperty = project.objects.fileProperty()

  @TaskAction
  @Suppress("unused")
  fun merge() {
    val binaryPaths = collectBinaryPaths()

    val relocatedPkgs = relocatedPackages.get()

    val relocatedPaths = relocatedPkgs.entries.associate { (key, value) -> toPath(key) to toPath(value) }

    // use negative lookbehind to match any that don't precede with
    // a word or a period character. should catch most cases.
    val importPattern = Pattern.compile("(?<!(\\w|\\.))(" +
        relocatedPkgs.keys.joinToString("|") { it.replace(".", "\\.") } + ")")

    val sourceFileExts = sourceFileExtensions.get()

    val outDir = this.temporaryDir

    for (jar in inputJars) {
      // as of Gradle 2.4, doesn't visit dirs despite the claims
      project.zipTree(jar).visit {
        val details = this
        if (details.isDirectory) return@visit

        var path = details.relativePath.parent.pathString
        val relocatedPath = relocatedPaths.keys.find { path.startsWith(it) }
        if (relocatedPath != null) {
          path = path.replace(relocatedPath, relocatedPaths.getValue(relocatedPath))
        }
        // conservative shrinking
        if (!binaryPaths.contains(path)) return@visit

        val outFile = File("$outDir/$path/${details.file.name}")
        outFile.parentFile.mkdirs()

        if (sourceFileExts.any { details.file.name.endsWith(it) }) {
          val oldContents = details.file.readText(Charsets.UTF_8)
          val newContents = fixImports(relocatedPkgs, details, oldContents, importPattern)
          outFile.writeText(newContents, Charsets.UTF_8)
        } else {
          details.copyTo(outFile)
        }
      }
    }

    project.ant.invokeMethod("jar", mapOf("basedir" to outDir, "destfile" to outputJar.get()))
  }

  private fun collectBinaryPaths(): Set<String> {
    val result = mutableSetOf<String>()
    for (jar in mergedBinaryJars) {
      // as of Gradle 2.4 doesn't visit dirs despite the claims
      project.zipTree(jar).visit {
        val details = this
        if (details.isDirectory) return@visit // avoid adding empty dirs
        result.add(details.relativePath.parent.pathString)
      }
    }
    return result
  }

  private fun fixImports(
    relocatedPkgs: Map<String, String>,
    details: FileVisitDetails,
    sourceText: String,
    importPattern: Pattern
  ): String {
    val matcher = importPattern.matcher(sourceText)
    val buffer = StringBuffer()
    logger.debug("Inspecting file: {}", details.relativePath)
    while (matcher.find()) {
      val newStat = relocatedPkgs[matcher.group(2)]
      logger.debug("Old: {}", matcher.group())
      logger.debug("New: {}", newStat)
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(newStat))
    }
    matcher.appendTail(buffer)
    return buffer.toString()
  }

  private fun toPath(packageName: String): String = packageName.replace(".", "/")
}
