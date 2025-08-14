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

  public static void distributeComments(Node root, List<Comment> comments) {
    for (var comment : comments) {
      var targetNode = findNearestNode(root, comment);
      var affixType = affixType(targetNode, comment);
      NodeEnhancements.addAffix(targetNode, comment.withAffixType(affixType));
    }
  }

  private static Node findNearestNode(Node root, Comment comment) {
    // If the comment appears to be within this node's span,
    // check children to find which one it's actually adjacent to
    if (within(root.span(), comment.span())) {
      Node nearestChild = null;
      var minDistance = Integer.MAX_VALUE;

      var children = root.children();
      if (children != null) {
        for (var child : children) {
          if (child == null) continue;
          var candidateNode = findNearestNode(child, comment);
          var distance = calculateDistance(candidateNode.span(), comment.span());
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

  private static boolean within(Span nodeSpan, Span commentSpan) {
    var nodeStart = nodeSpan.charIndex();
    var nodeEnd = nodeSpan.charIndex() + nodeSpan.length();
    var commentStart = commentSpan.charIndex();
    var commentEnd = commentSpan.charIndex() + commentSpan.length();

    return nodeStart <= commentStart && commentEnd <= nodeEnd;
  }

  private static int calculateDistance(Span nodeSpan, Span commentSpan) {
    var nodeStart = nodeSpan.charIndex();
    var nodeEnd = nodeSpan.charIndex() + nodeSpan.length();
    var commentStart = commentSpan.charIndex();
    var commentEnd = commentSpan.charIndex() + commentSpan.length();

    if (commentEnd <= nodeStart) {
      return nodeStart - commentEnd;
    }
    if (commentStart >= nodeEnd) {
      return commentStart - nodeEnd;
    }
    return 0;
  }

  private static AffixType affixType(Node node, Comment comment) {
    if (comment.span().charIndex() < node.span().charIndex()) {
      return AffixType.PREFIX;
    } else {
      return AffixType.SUFFIX;
    }
  }
}
