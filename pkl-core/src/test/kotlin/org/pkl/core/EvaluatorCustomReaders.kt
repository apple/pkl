/**
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
package org.pkl.core

import org.pkl.core.module.*
import org.pkl.core.module.ModuleKeys.DependencyAwareModuleKey
import org.pkl.core.packages.Dependency
import org.pkl.core.packages.PackageLoadError
import org.pkl.core.resource.Resource
import org.pkl.core.resource.ResourceReader
import org.pkl.core.runtime.VmContext
import org.pkl.core.util.IoUtils
import java.io.FileNotFoundException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * Provides Module- and Resource readers for use in tests
 * Readers are provided a URI scheme and
 */
class EvaluatorCustomReaders {
  
  class CustomModuleKey(uri: URI, private val basePath: Path) : DependencyAwareModuleKey(uri) {

    override fun hasHierarchicalUris(): Boolean = true
    override fun isGlobbable(): Boolean = true

    override fun hasElement(securityManager: SecurityManager, elementUri: URI): Boolean {
      securityManager.checkResolveModule(elementUri);
      val realPath = basePath.resolve(elementUri.path.removePrefix("/")).toRealPath()
      if (!realPath.startsWith(basePath)) {
        throw PklBugException("attempt to access path $realPath above base path $basePath")
      }
      return FileResolver.hasElement(realPath)
    }

    override fun listElements(securityManager: SecurityManager, baseUri: URI): MutableList<PathElement> {
      securityManager.checkResolveModule(baseUri)
      val realPath = basePath.resolve(baseUri.path.removePrefix("/")).toRealPath()
      if (!realPath.startsWith(basePath)) {
        throw PklBugException("attempt to access path $realPath above base path $basePath")
      }
      return FileResolver.listElements(realPath)
    }

    override fun resolve(securityManager: SecurityManager): ResolvedModuleKey {
      securityManager.checkResolveModule(uri);
      val realPath = basePath.toRealPath()
      if (!realPath.startsWith(basePath) && realPath != basePath) {
        throw PklBugException("attempt to access path $realPath above base path $basePath")
      }
      return ResolvedModuleKeys.file(this, realPath.toUri(), realPath)
    }

    override fun getDependencies(): MutableMap<String, out Dependency> {
      val projectDepsManager = VmContext.get(null).projectDependenciesManager
      if (projectDepsManager == null || !projectDepsManager.hasUri(uri)) {
        throw PackageLoadError("cannotResolveDependencyNoProject")
      }
      return projectDepsManager.dependencies
    }

    override fun cannotFindDependency(name: String): PackageLoadError {
      return PackageLoadError("cannotFindDependencyInProject", name)
    }

  }

  class CustomModuleKeyFactory(private val uriScheme: String, private val dir: Path) : ModuleKeyFactory {

    override fun create(uri: URI): Optional<ModuleKey> {
      if (uri.scheme != uriScheme) {
        return Optional.empty()
      }

      val resolvedPath = dir.resolve(Path.of(uri.path.removePrefix("/"))).toRealPath()
      return Optional.of(CustomModuleKey(uri, resolvedPath))
    }

  }

  class CustomResourceReader(private val customUriScheme: String, base: Path) : ResourceReader {
    
    private val basePath = base.toRealPath()
    override fun hasHierarchicalUris(): Boolean = true

    override fun isGlobbable(): Boolean = true

    override fun getUriScheme(): String = customUriScheme

    override fun hasElement(securityManager: SecurityManager, elementUri: URI): Boolean {
      securityManager.checkResolveModule(elementUri);
      val realPath = basePath.resolve(elementUri.path.removePrefix("/")).toRealPath()
      if (!realPath.startsWith(basePath)) {
        throw PklBugException("attempt to access path $realPath above base path $basePath")
      }
      return FileResolver.hasElement(realPath)
    }

    override fun listElements(securityManager: SecurityManager, baseUri: URI): MutableList<PathElement> {
      securityManager.checkResolveModule(baseUri)
      val realPath = basePath.resolve(baseUri.path.removePrefix("/")).toRealPath()
      if (!realPath.startsWith(basePath)) {
        throw PklBugException("attempt to access path $realPath above base path $basePath")
      }
      return FileResolver.listElements(realPath)
    }

    override fun read(uri: URI): Optional<Any> {
      val realPath = basePath.resolve(uri.path.removePrefix("/")).toRealPath()
      if (!realPath.startsWith(basePath)) {
        throw PklBugException("attempt to access path $realPath above base path $basePath")
      }
      
      try {
        val content = Files.readAllBytes(realPath)
        return Optional.of(Resource(uri, content))
      } catch (e: FileNotFoundException) {
        return Optional.empty()
      }
    }

  }

}
