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
package org.pkl.doc

import kotlin.io.path.bufferedWriter
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.pkl.commons.createParentDirectories
import org.pkl.commons.toPath
import org.pkl.core.*
import org.pkl.core.util.IoUtils

internal abstract class PageGenerator<out S>(
  protected val docsiteInfo: DocsiteInfo,
  protected val pageScope: S
) where S : PageScope {
  private val markdownInlineParserFactory = MarkdownParserFactory(pageScope)

  private val markdownParser =
    Parser.builder()
      .extensions(listOf(TablesExtension.create()))
      .inlineParserFactory(markdownInlineParserFactory)
      .build()

  private val markdownRenderer =
    HtmlRenderer.builder()
      .extensions(listOf(TablesExtension.create()))
      .nodeRendererFactory { MarkdownNodeRenderer(it) }
      .build()

  fun run() {
    val path = pageScope.url.toPath()
    path.createParentDirectories()
    path.bufferedWriter().use {
      it.appendLine("<!DOCTYPE html>")
      it.appendHTML().html(null, html)
    }
  }

  protected abstract val html: HTML.() -> Unit

  protected abstract fun HTMLTag.renderPageTitle()

  protected fun HTML.renderHtmlHead() {
    head {
      title { renderPageTitle() }
      script {
        src = pageScope.relativeSiteUrl.resolve("scripts/pkldoc.js").toString()
        defer = true
      }
      script {
        src = pageScope.relativeSiteUrl.resolve("scripts/scroll-into-view.min.js").toString()
        defer = true
      }
      if (pageScope !is SiteScope) {
        script {
          src = IoUtils.relativize(pageScope.dataUrl, pageScope.url).toString()
          defer = true
        }
      }
      link {
        href = pageScope.relativeSiteUrl.resolve("styles/pkldoc.css").toString()
        media = "screen"
        type = "text/css"
        rel = "stylesheet"
      }
      link {
        rel = "icon"
        type = "image/svg+xml"
        href = pageScope.relativeSiteUrl.resolve("images/favicon.svg").toString()
      }
      link {
        rel = "apple-touch-icon"
        sizes = "180x180"
        href = pageScope.relativeSiteUrl.resolve("images/apple-touch-icon.png").toString()
      }
      link {
        rel = "icon"
        type = "image/png"
        sizes = "32x32"
        href = pageScope.relativeSiteUrl.resolve("images/favicon-32x32.png").toString()
      }
      link {
        rel = "icon"
        type = "image/png"
        sizes = "16x16"
        href = pageScope.relativeSiteUrl.resolve("images/favicon-16x16.png").toString()
      }
      meta { charset = "UTF-8" }
    }
  }

  protected fun HtmlBlockTag.renderPageHeader(
    packageName: String?,
    packageVersion: String?,
    moduleName: String?,
    className: String?
  ) {
    header {
      if (docsiteInfo.title != null) {
        div {
          id = "doc-title"

          a {
            href = pageScope.relativeSiteUrl.toString()
            +docsiteInfo.title
          }
        }
      }

      div {
        id = "search"

        i {
          id = "search-icon"
          classes = setOf("material-icons")
          +"search"
        }

        input {
          id = "search-input"
          type = InputType.search
          placeholder =
            if (packageName == null) {
              "Click or press 'S' to search"
            } else {
              "Click or press 'S' to search this package"
            }
          autoComplete = false
          if (packageName != null) {
            require(packageVersion != null)
            attributes["data-package-name"] = packageName
            attributes["data-package-version"] = packageVersion
            attributes["data-package-url-prefix"] =
              "../".repeat(pageScope.relativePackageUrl.path.count { it == '/' })
          }
          if (moduleName != null) {
            attributes["data-module-name"] = moduleName
          }
          if (className != null) {
            attributes["data-class-name"] = className
          }
          attributes["data-root-url-prefix"] =
            "../".repeat(pageScope.relativeSiteUrl.path.count { it == '/' })
        }
      }
    }
  }

  protected fun HtmlBlockTag.renderParentLinks() {
    a {
      classes = setOf("declaration-parent-link")
      href = pageScope.relativeSiteUrl.toString()

      +(docsiteInfo.title ?: "Pkldoc")
    }

    val packageScope =
      when (pageScope) {
        is ClassScope -> pageScope.parent!!.parent
        is ModuleScope -> pageScope.parent
        else -> null
      }

    if (packageScope != null) {
      +" > "

      a {
        classes = setOf("declaration-parent-link")
        href = packageScope.urlRelativeTo(pageScope).toString()

        +packageScope.name
      }
    }

    val moduleScope =
      when (pageScope) {
        is ClassScope -> pageScope.parent
        else -> null
      }

    if (moduleScope != null) {
      +" > "

      a {
        classes = setOf("declaration-parent-link")
        href = moduleScope.urlRelativeTo(pageScope).toString()

        +moduleScope.name
      }
    }
  }

  protected fun HtmlBlockTag.renderClassExtendsClause(clazz: PClass, currScope: DocScope) {
    val superclass = clazz.superclass ?: return
    if (superclass.info != PClassInfo.Typed) {
      +" extends "
      renderType(clazz.supertype!!, currScope)
    }
  }

  protected fun HtmlBlockTag.renderModuleAmendsOrExtendsClause(module: ModuleSchema) {
    module.supermodule?.let { supermodule ->
      if (module.isAmend) +" amends " else +" extends "
      renderModuleName(supermodule.moduleName)
    }
  }

  protected fun HtmlBlockTag.renderMemberGroupLinks(
    vararg groups: Triple<String, String, Boolean>
  ) {
    ul {
      classes = setOf("member-group-links")
      for ((name, _href, show) in groups) {
        if (show) {
          li {
            a {
              href = _href
              +name
            }
          }
        }
      }
    }
  }

  protected fun HtmlBlockTag.renderModuleName(moduleName: String) {
    val moduleDocUrl = pageScope.resolveModuleNameToRelativeDocUrl(moduleName)

    if (moduleDocUrl != null) {
      a {
        href = moduleDocUrl.toString()
        classes = setOf("name-ref")
        +moduleName
      }
    } else {
      span {
        classes = setOf("member-ref")
        +moduleName
      }
    }
  }

  private val PClass.simpleDisplayName: String
    get() = if (isModuleClass) moduleName.substring(moduleName.lastIndexOf('.') + 1) else simpleName

  protected fun HtmlBlockTag.renderClassName(clazz: PClass, cssClass: String = "name-ref") {
    val moduleDocUrl = pageScope.resolveModuleNameToDocUrl(clazz.moduleName)

    if (moduleDocUrl != null) {
      val targetScope = ClassScope(clazz, moduleDocUrl, null)
      a {
        href = targetScope.urlRelativeTo(pageScope).toString()
        classes = setOf(cssClass)
        +clazz.simpleDisplayName.asIdentifier
      }
    } else {
      span {
        classes = setOf(cssClass)
        +clazz.simpleDisplayName.asIdentifier
      }
    }
  }

  protected fun HtmlBlockTag.renderTypeAliasName(
    typeAlias: TypeAlias,
    cssClass: String = "name-ref"
  ) {
    val moduleDocUrl = pageScope.resolveModuleNameToDocUrl(typeAlias.moduleName)

    if (moduleDocUrl != null) {
      val targetScope = TypeAliasScope(typeAlias, moduleDocUrl, null)
      a {
        href = targetScope.urlRelativeTo(pageScope).toString()
        classes = setOf(cssClass)
        +typeAlias.simpleName.asIdentifier
      }
    } else {
      span {
        classes = setOf(cssClass)
        +typeAlias.simpleName.asIdentifier
      }
    }
  }

  protected fun HtmlBlockTag.renderType(
    type: PType,
    currScope: DocScope,
    isNested: Boolean = false
  ) {
    when (type) {
      PType.UNKNOWN -> {
        +"unknown"
      }
      PType.NOTHING -> {
        +"nothing"
      }
      PType.MODULE -> {
        +"module"
      }
      is PType.StringLiteral -> {
        +"\"${type.literal}\""
      }
      is PType.Class -> {
        renderClassName(type.pClass)
        renderTypeArguments(type.typeArguments, currScope)
      }
      is PType.Nullable -> {
        renderType(type.baseType, currScope, true)
        +"?"
      }
      is PType.Union -> {
        if (isNested) +"("
        var first = true
        for (elem in type.elementTypes) {
          if (first) first = false else +"|"
          renderType(elem, currScope, true)
        }
        if (isNested) +")"
      }
      is PType.Function -> {
        +"("
        var first = true
        for (paramType in type.parameterTypes) {
          if (first) first = false else +", "
          renderType(paramType, currScope, true)
        }
        +")"

        +" -> "

        renderType(type.returnType, currScope, true)
      }
      is PType.Constrained -> {
        renderType(type.baseType, currScope, true)
        +"("
        var first = true
        for (constraint in type.constraints) {
          if (first) first = false else +", "
          +constraint
        }
        +")"
      }
      is PType.Alias -> {
        renderTypeAliasName(type.typeAlias)
        renderTypeArguments(type.typeArguments, currScope)
      }
      is PType.TypeVariable -> renderTypeVariable(type, currScope)
      else -> throw AssertionError("Unknown PType: $type")
    }
  }

  private fun HtmlBlockTag.renderTypeArguments(typeArguments: List<PType>, currentScope: DocScope) {
    if (typeArguments.isEmpty()) return

    +"<"
    var first = true
    for (typeArg in typeArguments) {
      if (first) first = false else +", "
      renderType(typeArg, currentScope, true)
    }
    +">"

    //    method.parameters.entries.forEachIndexed { idx, (name, type) ->
    //      if (first) first = false else +", "
    //      span {
    //        classes = setOf("param${indexOffset + idx + 1}")
    //        +name
    //      }
    //      +": "
    //      renderType(type, methodScope)
    //    }
  }

  private fun HtmlBlockTag.renderTypeVariable(
    typeVariable: PType.TypeVariable,
    currentScope: DocScope
  ) {
    val parameterScope = currentScope.resolveVariable(typeVariable.name) as? ParameterScope

    if (parameterScope != null) {
      a {
        href = parameterScope.urlRelativeTo(pageScope).toString()
        classes = setOf("name-ref")
        +typeVariable.name
      }
    } else {
      span {
        classes = setOf("name-ref")
        +typeVariable.name
      }
    }
  }

  protected fun HtmlBlockTag.renderModifiers(modifiers: Set<Modifier>, vararg additional: String) {
    for (modifier in modifiers) {
      +modifier.toString()
      +" "
    }
    for (modifier in additional) {
      +modifier
      +" "
    }
  }

  // best way I could find to offset anchors so that they aren't hidden behind fixed header when
  // navigated to
  // (tried several other CSS and JS solutions but all of them fell short in one way or another)
  // this solution works both for same-page and cross-page links, allows :target selector on
  // anchors, and requires no JS
  protected fun HtmlBlockTag.renderAnchor(anchorId: String, cssClass: String = "anchor") {
    div {
      id = anchorId.uriEncodedComponent
      classes = setOf(cssClass)
      +" " // needs some content to be considered a valid anchor by browsers
    }
  }

  protected fun HtmlBlockTag.renderAnchors(clazz: PClass) {
    clazz.typeParameters.forEachIndexed { idx, param ->
      renderAnchor(param.name, "anchor-param${idx + 1}")
    }
  }

  protected fun HtmlBlockTag.renderAnchors(typeAlias: TypeAlias) {
    val baseId = typeAlias.simpleName
    renderAnchor(baseId)
    typeAlias.typeParameters
      .map { it.name }
      .forEachIndexed { idx, param -> renderAnchor("$baseId.$param", "anchor-param${idx + 1}") }
  }

  protected fun HtmlBlockTag.renderAnchors(method: PClass.Method) {
    val baseId = "${method.simpleName}()"
    renderAnchor(baseId)
    (method.typeParameters.map { it.name } + method.parameters.keys).forEachIndexed { idx, param ->
      renderAnchor("$baseId.$param", "anchor-param${idx + 1}")
    }
  }

  protected fun HtmlBlockTag.renderSelfLink(memberName: String) {
    a {
      classes = setOf("member-selflink", "material-icons")
      href = "#${memberName.uriEncodedComponent}"
      +"link"
    }
  }

  protected val runtimeDataClasses: Set<String> = setOf("runtime-data", "hidden")

  protected fun collectMemberInfoForPackage(
    docPackage: DocPackage
  ): Map<MemberInfoKey, HtmlBlockTag.() -> Unit> {
    val result: MutableMap<MemberInfoKey, HtmlBlockTag.() -> Unit> = mutableMapOf()

    if (docPackage.minPklVersion != null) {
      result[MemberInfoKey("Pkl version")] = { +"${docPackage.minPklVersion} or higher" }
    }

    if (docPackage.uri != null) {
      result[MemberInfoKey("URI")] = {
        span {
          classes = setOf("import-uri")
          +docPackage.uri.toString()
        }
        i {
          classes = setOf("copy-uri-button", "material-icons")
          +"content_copy"
        }
      }
    }

    if (docPackage.docPackageInfo.authors?.isNotEmpty() == true) {
      result[MemberInfoKey("Authors")] = {
        var first = true
        for (author in docPackage.docPackageInfo.authors) {
          if (first) first = false else +", "
          +author
        }
      }
    }

    result[MemberInfoKey("Version")] = { +docPackage.version }

    if (docPackage.docPackageInfo.sourceCode != null) {
      val sources = docPackage.docPackageInfo.sourceCode.toString()
      result[MemberInfoKey("Source code")] = {
        a {
          href = sources
          +sources
        }
      }
    }

    if (docPackage.docPackageInfo.issueTracker != null) {
      val issues = docPackage.docPackageInfo.issueTracker.toString()
      result[MemberInfoKey("Issue tracker")] = {
        a {
          href = issues
          +issues
        }
      }
    }

    // Every package implicitly depends on `pkl`; omit to reduce noise.
    val dependencies = docPackage.docPackageInfo.dependencies.filter { it.name != "pkl" }
    if (dependencies.isNotEmpty()) {
      result[MemberInfoKey("Dependencies")] = {
        var first = true
        for (dep in dependencies) {
          if (first) first = false else +", "
          val text = "${dep.name}:${dep.version}"
          if (dep.documentation != null) {
            a {
              href = dep.documentation.toString()
              +text
            }
          } else {
            val localSitePackage =
              pageScope.siteScope?.packageScopes?.values?.find {
                it.docPackageInfo.name == dep.name
              }
            if (localSitePackage != null) {
              a {
                href =
                  pageScope.relativeSiteUrl
                    .resolve(
                      "${localSitePackage.docPackageInfo.name.pathEncoded}/current/index.html"
                    )
                    .toString()
                +text
              }
            } else {
              +text
            }
          }
        }
      }
    }

    for ((key, value) in docPackage.docPackageInfo.extraAttributes) {
      result[MemberInfoKey(key)] = { +value }
    }

    result[MemberInfoKey("Known usages", runtimeDataClasses)] = {
      id = HtmlConstants.KNOWN_USAGES
      classes = runtimeDataClasses
    }

    result[MemberInfoKey("All versions", runtimeDataClasses)] = {
      id = HtmlConstants.KNOWN_VERSIONS
      classes = runtimeDataClasses
    }

    return result
  }

  protected class MemberInfoKey(val name: String, val classes: Set<String> = setOf())

  protected fun collectMemberInfo(
    docModule: DocModule
  ): Map<MemberInfoKey, HtmlBlockTag.() -> Unit> {
    val importUri = docModule.importUri
    val sourceUrl = docModule.sourceUrl
    val examples = docModule.examples

    val result: MutableMap<MemberInfoKey, HtmlBlockTag.() -> Unit> = mutableMapOf()

    result[MemberInfoKey("Module URI")] = {
      span {
        classes = setOf("import-uri")
        +(importUri.toString())
      }
      i {
        classes = setOf("copy-uri-button", "material-icons")
        +"content_copy"
      }
    }

    val moduleInfoAnnotation =
      docModule.schema.annotations.find { it.classInfo == PClassInfo.ModuleInfo }
    if (moduleInfoAnnotation != null) {
      val minPklVersion = moduleInfoAnnotation["minPklVersion"] as String
      result[MemberInfoKey("Pkl version")] = { +"$minPklVersion or higher" }
    }

    if (sourceUrl != null) {
      result[MemberInfoKey("Source code")] = {
        a {
          href = sourceUrl.toString()
          val path = sourceUrl.path
          val name = path.substring(path.lastIndexOf("/") + 1)
          +name
        }
      }
    }

    if (examples.isNotEmpty() && docModule.parent.docPackageInfo.sourceCodeUrlScheme != null) {
      result[MemberInfoKey("Examples")] = {
        var first = true
        for (example in examples) {
          if (first) first = false else +", "
          a {
            href =
              docModule.parent.docPackageInfo.getModuleSourceCode(example.moduleName)!!.toString()
            +example.shortModuleName
          }
        }
      }
    }

    result[MemberInfoKey("Known subtypes", runtimeDataClasses)] = {
      id = HtmlConstants.KNOWN_SUBTYPES
      classes = runtimeDataClasses
    }

    result[MemberInfoKey("Known usages", runtimeDataClasses)] = {
      id = HtmlConstants.KNOWN_USAGES
      classes = runtimeDataClasses
    }

    result[MemberInfoKey("All versions", runtimeDataClasses)] = {
      id = HtmlConstants.KNOWN_VERSIONS
      classes = runtimeDataClasses
    }

    return result
  }

  protected inner class MemberDocs(
    docComment: String?,
    docScope: DocScope,
    annotations: List<PObject>,
    /** Whether these member docs are for the main declaration at the top of a page. */
    private val isDeclaration: Boolean = false,
    private val extraMemberInfo: Map<MemberInfoKey, HtmlBlockTag.() -> Unit> = mapOf()
  ) {
    init {
      markdownInlineParserFactory.docScope = docScope
    }

    private val summary: String? =
      docComment
        ?.let { getDocCommentSummary(it) }
        ?.let { markdownRenderer.render(markdownParser.parse(it)).trim().ifEmpty { null } }

    // whether to only show basic information without the option to expand
    private val showSummaryOnly: Boolean = !isDeclaration && docScope is PageScope

    private val overflow: String? =
      if (showSummaryOnly) {
        null // don't render if not needed
      } else {
        docComment
          ?.let { getDocCommentOverflow(it) }
          ?.let { markdownRenderer.render(markdownParser.parse(it)).trim().ifEmpty { null } }
      }

    private val deprecatedAnnotation: PObject? =
      annotations.find { it.classInfo == PClassInfo.Deprecated }

    private val alsoKnownAsAnnotation: PObject? =
      annotations.find { it.classInfo == PClassInfo.AlsoKnownAs }

    val isDeprecatedMember: Boolean = deprecatedAnnotation != null

    // whether there is a "member info" section consisting of key-value pairs
    private val hasMemberInfo: Boolean =
      extraMemberInfo.isNotEmpty() || alsoKnownAsAnnotation != null

    // whether the first paragraph of the user-provided doc comment
    // needs to give way for other information
    private val summaryMovesDown: Boolean = summary != null && deprecatedAnnotation != null

    val isExpandable: Boolean =
      !showSummaryOnly && (overflow != null || summaryMovesDown || hasMemberInfo && !isDeclaration)

    fun renderExpandIcon(tag: HtmlBlockTag) {
      if (isExpandable) {
        tag.classes += "with-expandable-docs"
        tag.i {
          classes = setOf("material-icons", "expandable-docs-icon")
          +"expand_more"
        }
      }
    }

    fun renderDocComment(tag: HtmlBlockTag) {
      if (deprecatedAnnotation != null) {
        val message = deprecatedAnnotation["message"] as String?

        val replaceWith = deprecatedAnnotation["replaceWith"] as String?

        tag.div {
          classes = setOf("doc-comment")
          if (message != null) {
            +"Deprecated: "
            unsafe { raw(renderInlineMarkdownText(message)) }
          } else {
            +"Deprecated."
          }
          if (replaceWith != null) {
            +" Replace with: "
            code { +replaceWith }
          }
        }
      } else if (summary != null) {
        tag.div {
          classes = setOf("doc-comment")

          unsafe { raw(summary) }
        }
      }

      if (showSummaryOnly) return

      if (hasMemberInfo) {
        tag.dl {
          classes =
            if (isExpandable && !isDeclaration) {
              setOf("member-info", "expandable", "hidden", "collapsed")
            } else {
              setOf("member-info")
            }

          for ((key, content) in extraMemberInfo) {
            dt {
              classes = key.classes
              +key.name
              +":"
            }
            dd { content() }
          }

          if (alsoKnownAsAnnotation != null) {
            dt { +"Also known as:" }
            dd {
              @Suppress("UNCHECKED_CAST") val names = alsoKnownAsAnnotation["names"] as List<String>
              var first = true
              for (name in names) {
                if (first) first = false else +", "
                code { +name }
              }
            }
          }
        }
      }

      if (summaryMovesDown || overflow != null) {
        tag.div {
          classes = setOf("doc-comment", "expandable", "hidden", "collapsed")

          unsafe {
            if (summaryMovesDown) raw(summary!!)
            if (overflow != null) raw(overflow)
          }
        }
      }
    }

    private fun renderInlineMarkdownText(text: String): String {
      var node = markdownParser.parse(text.trimIndent().trim())

      // unwrap top-level paragraphs because resulting HTML will be used as inline content
      while (node.firstChild != null && node.firstChild === node.lastChild) {
        node = node.firstChild
      }

      return markdownRenderer.render(node)
    }
  }
}
