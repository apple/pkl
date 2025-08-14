/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.parser.syntax.generic;

import java.util.List;
import org.pkl.parser.Span;
import org.pkl.parser.syntax.Node;

public class AffixDistributor {

  public static void distributeAffixes(Node root, List<Affix> affixes) {
    for (var affix : affixes) {
      var targetNode = findNearestNode(root, affix);
      var affixType = fixity(targetNode, affix);
      NodeEnhancements.addAffix(targetNode, affix.withAffixType(affixType));
    }
  }

  private static Node findNearestNode(Node root, Affix affix) {
    // If the comment appears to be within this node's span,
    // check children to find which one it's actually adjacent to
    if (within(root.span(), affix.span())) {
      Node nearestChild = null;
      var minDistance = Integer.MAX_VALUE;

      var children = root.children();
      if (children != null) {
        for (var child : children) {
          if (child == null) continue;
          var candidateNode = findNearestNode(child, affix);
          var distance = calculateDistance(candidateNode.span(), affix);
          if (distance < minDistance) {
            minDistance = distance;
            nearestChild = candidateNode;
          }
        }
      }

      if (nearestChild != null) {
        return nearestChild;
      }
    }

    // Comment is outside this node's span, or this node has no children
    return root;
  }

  private static boolean within(Span nodeSpan, Span affixSpan) {
    var nodeStart = nodeSpan.charIndex();
    var nodeEnd = nodeSpan.charIndex() + nodeSpan.length();
    var commentStart = affixSpan.charIndex();
    var commentEnd = affixSpan.charIndex() + affixSpan.length();

    return nodeStart <= commentStart && commentEnd <= nodeEnd;
  }

  private static int calculateDistance(Span nodeSpan, Affix affix) {
    var nodeStart = nodeSpan.charIndex();
    var nodeEnd = nodeSpan.charIndex() + nodeSpan.length();
    var affixSpan = affix.span();
    var commentStart = affixSpan.charIndex();
    var commentEnd = affixSpan.charIndex() + affixSpan.length();

    if (commentEnd <= nodeStart) {
      if (affix.type().alwaysSuffix) return Integer.MAX_VALUE;
      return nodeStart - commentEnd;
    }
    if (commentStart >= nodeEnd) {
      return commentStart - nodeEnd;
    }
    return 0;
  }

  private static AffixFixity fixity(Node node, Affix affix) {
    if (affix.span().charIndex() < node.span().charIndex()) {
      return AffixFixity.PREFIX;
    } else {
      return AffixFixity.SUFFIX;
    }
  }
}
