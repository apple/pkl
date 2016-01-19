/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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

import org.commonmark.internal.InlineParserImpl
import org.commonmark.node.Code
import org.commonmark.node.Link
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.Text
import org.commonmark.parser.InlineParser
import org.commonmark.parser.InlineParserContext
import org.commonmark.parser.InlineParserFactory
import org.commonmark.parser.delimiter.DelimiterProcessor
import org.commonmark.renderer.html.CoreHtmlNodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext

internal class MarkdownParserFactory(private val pageScope: DocScope) : InlineParserFactory {
  // the scope of the doc comment to be parsed next; mutated by [MemberDocs]
  var docScope: DocScope = pageScope

  override fun create(inlineParserContext: InlineParserContext): InlineParser {
    return InlineParserImpl(MarkdownParserContext(docScope, pageScope, inlineParserContext))
  }
}

internal class MarkdownParserContext(
  private val docScope: DocScope,
  private val pageScope: DocScope,
  private val delegate: InlineParserContext
) : InlineParserContext {
  companion object {
    private val keywords = setOf("null", "true", "false", "this", "unknown", "nothing")
  }

  private val seenLinkTargets = mutableSetOf<DocScope>()

  override fun getCustomDelimiterProcessors(): List<DelimiterProcessor> {
    return delegate.customDelimiterProcessors
  }

  /**
   * This method communicates with [MarkdownNodeRenderer] through the method's return value:
   * * `title = "pkldoc:1:$label"` -> replace link with `Code(label)`
   * * `title = "pkldoc:2:$label"` -> replace link text with `Code(label)`
   *
   * We only want to modify *short* reference links as described above. Whereas this method can't
   * tell whether it's resolving a short or full reference link, PklNodeRenderer can, by comparing
   * the link text to the passed through label.
   */
  override fun getLinkReferenceDefinition(label: String): LinkReferenceDefinition {
    if (label in keywords) {
      return LinkReferenceDefinition(label, label, "pkldoc:1:$label")
    }

    val destScope = docScope.resolveDocLink(label)
    val destination = destScope?.urlRelativeTo(pageScope)?.toString() ?: "not_found"
    val command =
      when {
        // dangling link
        destScope == null -> 2

        // don't link to ourselves
        destScope == docScope -> 1

        // don't link to our own method parameters
        destScope is ParameterScope && destScope.parent == docScope -> 1

        // only link to a target once
        !seenLinkTargets.add(destScope) -> 1
        else -> 2
      }

    return LinkReferenceDefinition(label, destination, "pkldoc:$command:$label")
  }
}

internal class MarkdownNodeRenderer(context: HtmlNodeRendererContext) :
  CoreHtmlNodeRenderer(context) {
  override fun visit(link: Link) {
    val title = link.title
    if (title == null || !title.startsWith("pkldoc:")) {
      super.visit(link)
      return
    }

    // see [MarkdownParserContext] for contents of `title`
    val command = title[7]
    val label = title.substring(9)
    val isShortReferenceLink =
      link.firstChild === link.lastChild && (link.firstChild as? Text)?.literal == label

    when {
      !isShortReferenceLink -> super.visit(link.apply { this.title = null })
      command == '1' -> visit(Code(label))
      command == '2' -> visit(Link(link.destination, null).apply { appendChild(Code(label)) })
      else -> throw AssertionError("Unknown command: $command")
    }
  }
}
