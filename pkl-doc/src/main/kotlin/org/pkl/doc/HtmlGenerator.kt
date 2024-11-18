/*
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.doc

import java.net.URI
import java.nio.file.Path
import org.pkl.core.ModuleSchema

internal class HtmlGenerator(
  private val docsiteInfo: DocsiteInfo,
  docPackages: List<DocPackage>,
  importResolver: (URI) -> ModuleSchema,
  private val outputDir: Path,
  private val isTestMode: Boolean
) {
  private val siteScope =
    SiteScope(docPackages, docsiteInfo.overviewImports, importResolver, outputDir)

  fun generate(docPackage: DocPackage) {
    val packageScope = siteScope.getPackage(docPackage.name)

    PackagePageGenerator(docsiteInfo, docPackage, packageScope).run()

    for (docModule in docPackage.docModules) {
      if (docModule.isUnlisted) continue

      val moduleScope = packageScope.getModule(docModule.name)

      ModulePageGenerator(docsiteInfo, docPackage, docModule, moduleScope, isTestMode).run()

      for ((_, clazz) in docModule.schema.classes) {
        if (clazz.isUnlisted) continue

        ClassPageGenerator(
            docsiteInfo,
            docPackage,
            docModule,
            clazz,
            ClassScope(clazz, moduleScope.url, moduleScope),
            isTestMode
          )
          .run()
      }
    }
  }

  fun generateSite(packagesData: List<PackageData>) {
    MainPageGenerator(docsiteInfo, packagesData, siteScope).run()

    generateStaticResources()
  }

  private fun generateStaticResources() {
    copyResource("fonts/lato-v14-latin_latin-ext-regular.woff2", outputDir)
    copyResource("fonts/lato-v14-latin_latin-ext-700.woff2", outputDir)

    copyResource("fonts/open-sans-v15-latin_latin-ext-regular.woff2", outputDir)
    copyResource("fonts/open-sans-v15-latin_latin-ext-italic.woff2", outputDir)
    copyResource("fonts/open-sans-v15-latin_latin-ext-700.woff2", outputDir)
    copyResource("fonts/open-sans-v15-latin_latin-ext-700italic.woff2", outputDir)

    copyResource("fonts/source-code-pro-v7-latin_latin-ext-regular.woff2", outputDir)
    copyResource("fonts/source-code-pro-v7-latin_latin-ext-700.woff2", outputDir)

    copyResource("fonts/MaterialIcons-Regular.woff2", outputDir)

    copyResource("scripts/pkldoc.js", outputDir)
    copyResource("scripts/search-worker.js", outputDir)
    copyResource("scripts/scroll-into-view.min.js", outputDir)

    copyResource("styles/pkldoc.css", outputDir)

    copyResource("images/apple-touch-icon.png", outputDir)
    copyResource("images/favicon.svg", outputDir)
    copyResource("images/favicon-16x16.png", outputDir)
    copyResource("images/favicon-32x32.png", outputDir)
  }
}
