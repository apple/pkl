import org.gradle.api.Project
import org.gradle.api.file.FileCollection

open class HtmlValidator(project: Project) {
  var sources: FileCollection = project.files()
}