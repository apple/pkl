/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import groovy.util.Node
import groovy.xml.XmlParser
import groovy.xml.XmlUtil
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class ConfigureLateInitAnnotation : DefaultTask() {
  private val miscXmlFile = project.rootProject.file(".idea/misc.xml")

  init {
    inputs.file(miscXmlFile)
    outputs.file(miscXmlFile)
  }

  @TaskAction
  fun run() {
    val annotationName = "org.pkl.core.util.LateInit"

    if (!miscXmlFile.exists()) {
      miscXmlFile.writeText(
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
        </project>
        """
          .trimIndent()
          .trim()
      )
    }

    val root = XmlParser().parse(miscXmlFile)

    fun Node.childNodes() = children().filterIsInstance<Node>()

    var entryPointsManager =
      root.childNodes().find {
        it.name() == "component" && it.attribute("name") == "EntryPointsManager"
      }
    if (entryPointsManager == null) {
      entryPointsManager = root.appendNode("component", mapOf("name" to "EntryPointsManager"))
    }

    var writeAnnotations = entryPointsManager.childNodes().find { it.name() == "writeAnnotations" }
    if (writeAnnotations == null) {
      writeAnnotations = entryPointsManager.appendNode("writeAnnotations")
    }

    val alreadyExists =
      writeAnnotations.childNodes().any {
        it.name() == "writeAnnotation" && it.attribute("name") == annotationName
      }

    if (!alreadyExists) {
      writeAnnotations.appendNode("writeAnnotation", mapOf("name" to annotationName))
      miscXmlFile.writeText(XmlUtil.serialize(root))
      logger.lifecycle("Updated .idea/misc.xml")
    } else {
      logger.info("$annotationName is already configured in .idea/misc.xml")
    }
  }
}
