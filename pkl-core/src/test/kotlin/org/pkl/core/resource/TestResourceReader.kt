package org.pkl.core.resource

import java.net.URI
import java.util.*

class TestResourceReader : ResourceReader {
  override fun hasHierarchicalUris(): Boolean = false

  override fun isGlobbable(): Boolean = false

  override fun getUriScheme(): String = "test"

  override fun read(uri: URI): Optional<Any> = Optional.of("success")
}
