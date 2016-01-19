package org.pkl.core.module

import java.net.URI
import java.util.*

class TestModuleKeyFactory : ModuleKeyFactory {
  override fun create(uri: URI): Optional<ModuleKey> =
    if (uri.scheme == "test") {
      ModuleKeyFactories.classPath(this::class.java.classLoader)
        .create(URI("modulepath:/org/pkl/core/module/testFactoryTest.pkl"))
    } else {
      Optional.empty<ModuleKey>()
    }
}
