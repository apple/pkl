package org.pkl.core.runtime

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.FileSystems

class FileSystemManagerTest {
  private val resource = 
    javaClass.getResource("/org/pkl/core/resource/resource1.jar")!!.toURI()
  private val resourceUri = URI("jar:$resource")

  @Test
  fun `only closes a file system after the last usage closes`() {
    val fs1 = FileSystemManager.getFileSystem(resourceUri)
    val fs2 = FileSystemManager.getFileSystem(resourceUri)
    val fs3 = FileSystemManager.getFileSystem(resourceUri)
    fs1.close()
    assertTrue(fs1.isOpen)
    assertTrue(fs2.isOpen)
    assertTrue(fs3.isOpen)
    fs2.close()
    assertTrue(fs1.isOpen)
    assertTrue(fs2.isOpen)
    assertTrue(fs3.isOpen)
    fs3.close()
    assertFalse(fs1.isOpen)
    assertFalse(fs2.isOpen)
    assertFalse(fs3.isOpen)
  }

  @Test
  fun `does not close file system that was spawned externally`() {
    FileSystems.newFileSystem(resourceUri, emptyMap<String, Any>()).use { fs ->
      val fs2 = FileSystemManager.getFileSystem(resourceUri)
      assertTrue(fs.isOpen)
      assertTrue(fs2.isOpen)
      fs2.close()
      assertTrue(fs.isOpen)
      assertTrue(fs2.isOpen)
    }
  }

  @Test
  fun `close and re-open same file system`() {
    val fs = FileSystemManager.getFileSystem(resourceUri)
    assertTrue(fs.isOpen)
    fs.close()
    assertFalse(fs.isOpen)
    val fs2 = FileSystemManager.getFileSystem(resourceUri)
    assertTrue(fs2.isOpen)
    fs2.close()
    assertFalse(fs2.isOpen)
  }
}
