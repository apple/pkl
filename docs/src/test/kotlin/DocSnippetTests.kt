import org.junit.platform.commons.annotation.Testable
import org.junit.platform.engine.*
import org.junit.platform.engine.TestDescriptor.Type
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.discovery.MethodSelector
import org.junit.platform.engine.discovery.PackageSelector
import org.junit.platform.engine.discovery.UniqueIdSelector
import org.junit.platform.engine.support.descriptor.*
import org.junit.platform.engine.support.hierarchical.EngineExecutionContext
import org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine
import org.junit.platform.engine.support.hierarchical.Node
import org.junit.platform.engine.support.hierarchical.Node.DynamicTestExecutor
import org.opentest4j.MultipleFailuresError
import org.pkl.commons.test.FileTestUtils.rootProjectDir
import org.pkl.core.Loggers
import org.pkl.core.SecurityManagers
import org.pkl.core.StackFrameTransformers
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.parser.LexParseException
import org.pkl.core.parser.Parser
import org.pkl.core.parser.antlr.PklParser
import org.pkl.core.repl.ReplRequest
import org.pkl.core.repl.ReplResponse
import org.pkl.core.repl.ReplServer
import org.pkl.core.resource.ResourceReaders
import org.pkl.core.util.IoUtils
import org.antlr.v4.runtime.ParserRuleContext
import org.pkl.core.http.HttpClient
import java.nio.file.Files
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.useDirectoryEntries

@Testable
class DocSnippetTests

class DocSnippetTestsEngine : HierarchicalTestEngine<DocSnippetTestsEngine.ExecutionContext>() {
  private val projectDir = rootProjectDir.resolve("docs")
  private val docsDir = projectDir.resolve("modules")

  companion object {
    val headingRegex = Regex("""(?u)^\s*(=++)\s*(.+)""")
    val collapsibleBlockRegex = Regex("""(?u)^\s*\[%collapsible""")
    val codeBlockRegex = Regex("""(?u)^\s*\[source(?:%(tested|parsed)(%error)?)?(?:,\{?([a-zA-Z-_]+)}?)?""")
    val codeBlockNameRegex = Regex("""(?u)^\s*\.(.+)""")
    val codeBlockDelimiterRegex = Regex("""(?u)^\s*----""")
    val graphicsRegex = Regex("\\[small]#.+#")
  }

  override fun getId() = "pkl-doc-tests"

  override fun discover(
    discoveryRequest: EngineDiscoveryRequest,
    uniqueId: UniqueId
  ): TestDescriptor {
    val packageSelectors = discoveryRequest.getSelectorsByType(PackageSelector::class.java)
    val classSelectors = discoveryRequest.getSelectorsByType(ClassSelector::class.java)
    val methodSelectors = discoveryRequest.getSelectorsByType(MethodSelector::class.java)
    val uniqueIdSelectors = discoveryRequest.getSelectorsByType(UniqueIdSelector::class.java)

    val testClass = DocSnippetTests::class.java
    val packageName = testClass.`package`.name
    val className = testClass.name

    if (methodSelectors.isEmpty()
      && (packageSelectors.isEmpty() || packageSelectors.any { it.packageName == packageName })
      && (classSelectors.isEmpty() || classSelectors.any { it.className == className })
    ) {

      val rootDescriptor = Descriptor.Path(uniqueId, docsDir.fileName.toString(), ClassSource.from(testClass), docsDir)
      doDiscover(rootDescriptor, uniqueIdSelectors)
      return rootDescriptor
    }

    // return empty descriptor w/o children
    return EngineDescriptor(uniqueId, javaClass.simpleName)
  }

  override fun createExecutionContext(request: ExecutionRequest): ExecutionContext {
    val replServer = ReplServer(
      SecurityManagers.defaultManager,
      HttpClient.dummyClient(),
      Loggers.stdErr(),
      listOf(
        ModuleKeyFactories.standardLibrary,
        ModuleKeyFactories.classPath(DocSnippetTests::class.java.classLoader),
        ModuleKeyFactories.file
      ),
      listOf(
        ResourceReaders.environmentVariable(),
        ResourceReaders.externalProperty()
      ),
      System.getenv(),
      emptyMap(),
      null,
      null,
      null,
      IoUtils.getCurrentWorkingDir(),
      StackFrameTransformers.defaultTransformer
    )
    return ExecutionContext(replServer)
  }

  private fun doDiscover(rootDescriptor: TestDescriptor, selectors: List<UniqueIdSelector>) {
    fun isMatch(other: UniqueId) = selectors.isEmpty() || selectors.any {
      it.uniqueId.hasPrefix(other) || other.hasPrefix(it.uniqueId)
    }

    docsDir.useDirectoryEntries { docsDirEntries ->
      for (docsDirEntry in docsDirEntries) {
        if (!docsDirEntry.isDirectory()) continue

        val docsDirEntryName = docsDirEntry.fileName.toString()
        val docsDirEntryId = rootDescriptor.uniqueId.append("dir", docsDirEntryName)
        if (!isMatch(docsDirEntryId)) continue

        val docsDirEntryDescriptor = Descriptor.Path(
          docsDirEntryId,
          docsDirEntryName,
          DirectorySource.from(docsDirEntry.toFile()),
          docsDirEntry
        )
        rootDescriptor.addChild(docsDirEntryDescriptor)

        val pagesDir = docsDirEntry.resolve("pages")
        if (!pagesDir.isDirectory()) continue

        pagesDir.useDirectoryEntries { pagesDirEntries ->
          for (pagesDirEntry in pagesDirEntries) {
            val pagesDirEntryName = pagesDirEntry.fileName.toString()
            val pagesDirEntryId = docsDirEntryId.append("file", pagesDirEntryName)
            if (!pagesDirEntry.isRegularFile() ||
              !pagesDirEntryName.endsWith(".adoc") ||
              !isMatch(pagesDirEntryId)
            ) continue

            val pagesDirEntryDescriptor = Descriptor.Path(
              pagesDirEntryId,
              pagesDirEntryName,
              FileSource.from(pagesDirEntry.toFile()),
              pagesDirEntry
            )
            docsDirEntryDescriptor.addChild(pagesDirEntryDescriptor)

            parseAsciidoc(pagesDirEntryDescriptor, selectors)
          }
        }
      }
    }
  }

  private fun parseAsciidoc(docDescriptor: Descriptor.Path, selectors: List<UniqueIdSelector>) {
    var line = ""
    var prevLine = ""
    var lineNum = 0
    var codeBlockNum = 0
    val sections = ArrayDeque<Descriptor.Section>()

    Files.lines(docDescriptor.path).use { linesStream ->
      val linesIterator = linesStream.iterator()

      fun advance() {
        prevLine = line
        line = linesIterator.next()
        lineNum += 1
      }

      fun addSection(title: String, newLevel: Int) {
        while (sections.isNotEmpty() && sections.first().level >= newLevel) {
          sections.removeFirst()
        }

        val parent = sections.firstOrNull() ?: docDescriptor
        val normalizedTitle = title
          .replace("<code>", "")
          .replace("</code>", "")
          .replace(graphicsRegex, "")
          .trim()
        val childSection = Descriptor.Section(
          parent.uniqueId.append("section", normalizedTitle),
          normalizedTitle,
          FileSource.from(docDescriptor.path.toFile(), FilePosition.from(lineNum)),
          newLevel
        )

        sections.addFirst(childSection)
        parent.addChild(childSection)
        codeBlockNum = 0
      }

      nextLine@ while (linesIterator.hasNext()) {
        advance()

        val headingMatch = headingRegex.find(line)
        if (headingMatch != null) {
          val (markup, title) = headingMatch.destructured
          val newLevel = markup.length
          // ignore level 1 heading (we already have a test node for the file)
          if (newLevel == 1) continue
          addSection(title, newLevel)
          continue
        }

        val collapsibleBlockMatch = collapsibleBlockRegex.find(line)
        if (collapsibleBlockMatch != null) {
          val blockName = codeBlockNameRegex.find(prevLine)?.groupValues?.get(1) ?: "Details"
          val newLevel = 999
          addSection(blockName, newLevel)
          continue
        }

        val codeBlockMatch = codeBlockRegex.find(line)
        if (codeBlockMatch != null) {
          codeBlockNum += 1
          val (testMode, error, language) = codeBlockMatch.destructured
          if (testMode.isNotEmpty()) {
            val blockName = codeBlockNameRegex.find(prevLine)?.groupValues?.get(1) ?: "snippet$codeBlockNum"
            while (linesIterator.hasNext()) {
              advance()
              val startDelimiterMatch = codeBlockDelimiterRegex.find(line)
              if (startDelimiterMatch != null) {
                val jumpToLineNum = lineNum + 1
                val builder = StringBuilder()
                while (linesIterator.hasNext()) {
                  advance()
                  val endDelimiterMatch = codeBlockDelimiterRegex.find(line)
                  if (endDelimiterMatch != null) {
                    val section = sections.first()
                    val snippetId = section.uniqueId.append("snippet", blockName)
                    if (selectors.isEmpty() || selectors.any { snippetId.hasPrefix(it.uniqueId) }) {
                      section.addChild(
                        Descriptor.Snippet(
                          snippetId,
                          blockName,
                          language,
                          FileSource.from(docDescriptor.path.toFile(), FilePosition.from(jumpToLineNum)),
                          builder.toString(),
                          testMode == "parsed",
                          error.isNotEmpty()
                        )
                      )
                    }
                    continue@nextLine
                  }
                  builder.appendLine(line)
                }
              }
            }
          }
        }
      }
    }
  }

  class ExecutionContext(val replServer: ReplServer) : EngineExecutionContext, AutoCloseable {
    override fun close() {
      replServer.close()
    }
  }

  private sealed class Descriptor(
    uniqueId: UniqueId,
    displayName: String,
    source: TestSource
  ) : AbstractTestDescriptor(uniqueId, displayName, source), Node<ExecutionContext> {

    class Path(
      uniqueId: UniqueId,
      displayName: String,
      source: TestSource,
      val path: java.nio.file.Path
    ) : Descriptor(uniqueId, displayName, source) {

      override fun getType() = Type.CONTAINER
    }

    class Section(
      uniqueId: UniqueId,
      displayName: String,
      source: TestSource,
      val level: Int
    ) : Descriptor(uniqueId, displayName, source) {

      override fun getType() = Type.CONTAINER

      override fun before(context: ExecutionContext): ExecutionContext {
        context.replServer.handleRequest(ReplRequest.Reset("reset"))
        return context
      }
    }

    class Snippet(
      uniqueId: UniqueId,
      displayName: String,
      private val language: String,
      source: TestSource,
      private val code: String,
      private val parseOnly: Boolean,
      private val expectError: Boolean
    ) : Descriptor(uniqueId, displayName, source) {

      override fun getType() = Type.TEST

      private val parsed: ParserRuleContext by lazy {
        when (language) {
          "pkl" -> Parser().parseModule(code)
          "pkl-expr" -> Parser().parseExpressionInput(code)
          else -> throw(Exception("Unrecognized language: $language"))
        }
      }

      override fun execute(context: ExecutionContext, executor: DynamicTestExecutor): ExecutionContext {
        if (parseOnly) {
          try {
            parsed
            if (expectError) {
              throw AssertionError("Expected a parse error, but got none.")
            }
          } catch (e: LexParseException) {
            if (!expectError) {
              throw AssertionError(e.message)
            }
          }
          return context
        }

        context.replServer.handleRequest(
          ReplRequest.Eval(
            "snippet",
            code,
            !expectError,
            !expectError
          )
        )

        val properties = parsed.children.filterIsInstance<PklParser.ClassPropertyContext>()

        val responses = mutableListOf<ReplResponse>()

        // force each property
        for (prop in properties) {
          responses.addAll(context.replServer.handleRequest(
            ReplRequest.Eval(
              "snippet",
              prop.Identifier().text,
              false,
              true
            )
          ))
        }
        if (expectError) {
          if (responses.dropLast(1).any { it !is ReplResponse.EvalSuccess } ||
            responses.last() !is ReplResponse.EvalError) {
            throw AssertionError(
              "Expected %error snippet to fail at the end, but got the following REPL responses:\n\n" +
                  responses.joinToString("\n\n") { it.message })
          }

          return context
        }

        val badResponses = responses.filter { it !is ReplResponse.EvalSuccess }
        if (badResponses.isNotEmpty()) {
          throw MultipleFailuresError(null, badResponses.map { AssertionError(it.message) })
        }

        return context
      }
    }
  }
}
