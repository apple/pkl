/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.stdlib.syntax;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import java.util.ArrayList;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.SyntaxModule;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.VmObjectFactory;
import org.pkl.formatter.Formatter;
import org.pkl.formatter.GrammarVersion;
import org.pkl.parser.GenericParser;
import org.pkl.parser.GenericParserError;
import org.pkl.parser.syntax.generic.FullSpan;
import org.pkl.parser.syntax.generic.Node;
import org.pkl.parser.syntax.generic.NodeType;

public final class SyntaxNodes {
  private SyntaxNodes() {}

  private static final Identifier TYPE_ID = Identifier.get("type");
  private static final Identifier CHILDREN_ID = Identifier.get("children");
  private static final Identifier SPAN_ID = Identifier.get("span");
  private static final Identifier LINE_START_ID = Identifier.get("lineStart");
  private static final Identifier COL_START_ID = Identifier.get("colStart");
  private static final Identifier LINE_END_ID = Identifier.get("lineEnd");
  private static final Identifier COL_END_ID = Identifier.get("colEnd");
  private static final char[] EMPTY_SOURCE = new char[0];

  /** Extra storage backing a Pkl {@code Node} instance. */
  static final class NodeData {
    final Node node;
    final char[] source;
    VmTyped parentVm;
    VmList childrenVm;
    VmTyped spanVm;

    NodeData(Node node, char[] source) {
      this.node = node;
      this.source = source;
    }
  }

  /** Extra storage backing a Pkl {@code ParserError} instance. */
  static final class ErrorData {
    final String text;
    final VmTyped spanVm;

    ErrorData(String text, VmTyped spanVm) {
      this.text = text;
      this.spanVm = spanVm;
    }
  }

  private static final VmObjectFactory<FullSpan> spanFactory =
      new VmObjectFactory<FullSpan>(SyntaxModule::getSpanClass)
          .addIntProperty("lineStart", FullSpan::lineBegin)
          .addIntProperty("colStart", FullSpan::colBegin)
          .addIntProperty("lineEnd", FullSpan::lineEnd)
          .addIntProperty("colEnd", FullSpan::colEnd);

  private static final VmObjectFactory<NodeData> nodeFactory =
      new VmObjectFactory<NodeData>(SyntaxModule::getNodeClass)
          .addStringProperty("type", nd -> nd.node.type.name().toLowerCase())
          .addListProperty("children", nd -> nd.childrenVm)
          .addProperty("parent", nd -> VmNull.lift(nd.parentVm))
          .addProperty(
              "text",
              nd -> nd.node.children.isEmpty() ? nd.node.text(nd.source) : VmNull.withoutDefault())
          .addTypedProperty("span", nd -> nd.spanVm);

  private static final VmObjectFactory<ErrorData> parserErrorFactory =
      new VmObjectFactory<ErrorData>(SyntaxModule::getParserErrorClass)
          .addStringProperty("text", ed -> ed.text)
          .addTypedProperty("span", ed -> ed.spanVm);

  public abstract static class parseNodes extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self, String source) {
      var sourceChars = source.toCharArray();

      try {
        var parser = new GenericParser();
        var root = parser.parseModule(source);
        return convertNode(root, sourceChars);
      } catch (GenericParserError e) {
        var errorSpanVm = spanFactory.create(e.getSpan());
        var text = e.getMessage() != null ? e.getMessage() : "Parse error";
        return parserErrorFactory.create(new ErrorData(text, errorSpanVm));
      }
    }

    private static VmTyped convertNode(Node genericNode, char[] sourceChars) {
      // convert children recursively
      var childrenList = new ArrayList<VmTyped>(genericNode.children.size());
      for (var child : genericNode.children) {
        childrenList.add(convertNode(child, sourceChars));
      }

      var data = new NodeData(genericNode, sourceChars);
      data.childrenVm = VmList.create(childrenList.toArray());
      data.spanVm = spanFactory.create(genericNode.span);

      var result = nodeFactory.create(data);

      // set parent back-reference on each child
      for (var childVm : childrenList) {
        var childData = (NodeData) childVm.getExtraStorage();
        childData.parentVm = result;
      }

      return result;
    }
  }

  public abstract static class formatToString extends ExternalMethod2Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self, VmTyped nodeVm, String grammarVersion) {
      var node = convertVmToNode(nodeVm);
      return new Formatter(GrammarVersion.valueOf(grammarVersion)).format(node);
    }
  }

  private static Node convertVmToNode(VmTyped nodeVm) {
    var typeStr = (String) VmUtils.readMember(nodeVm, TYPE_ID);
    var nodeType = NodeType.valueOf(typeStr.toUpperCase());

    var childrenVm = (VmList) VmUtils.readMember(nodeVm, CHILDREN_ID);
    var children = new ArrayList<Node>(childrenVm.getLength());
    for (var i = 0; i < childrenVm.getLength(); i++) {
      children.add(convertVmToNode((VmTyped) childrenVm.get(i)));
    }

    var spanVm = (VmTyped) VmUtils.readMember(nodeVm, SPAN_ID);
    var lineStart = ((Long) VmUtils.readMember(spanVm, LINE_START_ID)).intValue();
    var colStart = ((Long) VmUtils.readMember(spanVm, COL_START_ID)).intValue();
    var lineEnd = ((Long) VmUtils.readMember(spanVm, LINE_END_ID)).intValue();
    var colEnd = ((Long) VmUtils.readMember(spanVm, COL_END_ID)).intValue();
    var span = new FullSpan(0, 0, lineStart, colStart, lineEnd, colEnd);

    Node node;
    if (children.isEmpty()) {
      node = new Node(nodeType, span);
    } else {
      node = new Node(nodeType, span, children);
    }

    var textObj = VmUtils.readMember(nodeVm, Identifier.TEXT);
    if (textObj instanceof String text) {
      node.setText(text);
    } else if (nodeType == NodeType.STRING_CHARS) {
      var sb = new StringBuilder();
      for (var child : children) {
        sb.append(child.text(EMPTY_SOURCE));
      }
      node.setText(sb.toString());
    }

    return node;
  }
}
