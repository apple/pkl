/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.io.OutputStream
import java.net.URI
import java.nio.file.Path
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.pkl.core.ModuleSchema

internal class HtmlGenerator(
  private val docsiteInfo: DocsiteInfo,
  docPackages: List<DocPackage>,
  importResolver: (URI) -> ModuleSchema,
  private val outputDir: Path,
  private val isTestMode: Boolean,
  consoleOut: OutputStream,
) : AbstractGenerator(consoleOut) {
  private val siteScope =
    SiteScope(docPackages, docsiteInfo.overviewImports, importResolver, outputDir)

  suspend fun generate(docPackage: DocPackage) = coroutineScope {
    val packageScope = siteScope.getPackage(docPackage.docPackageInfo)
    launch { PackagePageGenerator(docsiteInfo, docPackage, packageScope, consoleOut).run() }

    for (docModule in docPackage.docModules) {
      if (docModule.isUnlisted) continue

      val moduleScope = packageScope.getModule(docModule.name)
      launch {
        ModulePageGenerator(docsiteInfo, docPackage, docModule, moduleScope, isTestMode, consoleOut)
          .run()
      }

      for ((_, clazz) in docModule.schema.classes) {
        if (clazz.isUnlisted) continue
        launch {
          ClassPageGenerator(
              docsiteInfo,
              docPackage,
              docModule,
              clazz,
              ClassScope(clazz, moduleScope.url, moduleScope),
              isTestMode,
              consoleOut,
            )
            .run()
        }
      }
    }
  }

  suspend fun generateSite(packages: List<PackageData>) = coroutineScope {
    launch { MainPageGenerator(docsiteInfo, packages, siteScope, consoleOut).run() }
    launch { generateStaticResources() }
  }

  private suspend fun generateStaticResources() = coroutineScope {
    val resources =
      listOf(
        "fonts/lato-v14-latin_latin-ext-regular.woff2",
        "fonts/lato-v14-latin_latin-ext-700.woff2",
        "fonts/open-sans-v15-latin_latin-ext-regular.woff2",
        "fonts/open-sans-v15-latin_latin-ext-italic.woff2",
        "fonts/open-sans-v15-latin_latin-ext-700.woff2",
        "fonts/open-sans-v15-latin_latin-ext-700italic.woff2",
        "fonts/source-code-pro-v7-latin_latin-ext-regular.woff2",
        "fonts/source-code-pro-v7-latin_latin-ext-700.woff2",
        "fonts/MaterialIcons-Regular.woff2",
        "scripts/pkldoc.js",
        "scripts/search-worker.js",
        "scripts/scroll-into-view.min.js",
        "styles/pkldoc.css",
        "images/apple-touch-icon.png",
        "images/favicon.svg",
        "images/favicon-16x16.png",
        "images/favicon-32x32.png",
      )
    for (resource in resources) {
      launch { copyResource(resource, outputDir) }
    }
  }
}
