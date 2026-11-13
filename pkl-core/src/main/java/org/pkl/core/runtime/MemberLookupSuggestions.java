/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.runtime;

import java.util.*;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.MemberLookupMode;
import org.pkl.core.ast.PklNode;
import org.pkl.core.ast.member.ClassMethod;
import org.pkl.core.ast.member.Member;
import org.pkl.core.runtime.MemberLookupSuggestions.Candidate.Kind;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.StringSimilarity;

public final class MemberLookupSuggestions {
  private static final StringSimilarity STRING_SIMILARITY = new StringSimilarity();
  // 0.77 is just about low enough to consider two three-character
  // names that differ in their first character similar
  private static final double SIMILARITY_THRESHOLD = 0.77;

  private final VmObjectLike receiver;
  private final VmObjectLike owner;
  private final Object memberName;
  // -1 for property, #arguments for function
  private final int memberArity;

  private final Set<Candidate.Kind> memberKinds;
  private final Set<Candidate> candidates = new LinkedHashSet<>();

  public MemberLookupSuggestions(
      VmObjectLike receiver,
      VmObjectLike owner,
      Object memberName,
      int memberArity,
      Set<Kind> memberKinds) {
    this.receiver = receiver;
    this.owner = owner;
    this.memberName = memberName;
    this.memberArity = memberArity;
    this.memberKinds = memberKinds;
  }

  // use same search order as in member lookup,
  // so that in case of members with same name,
  // (only) the correct one is suggested
  public List<Candidate> find(MemberLookupMode memberLookupMode, @Nullable PklNode receiverNode) {
    candidates.clear();
    if (memberLookupMode.isImplicitReceiver()) {
      for (var curr = receiver; curr != null; curr = curr.getEnclosingOwner()) {
        addPropertyCandidates(curr, true, memberLookupMode, receiverNode);
        if (curr.isPrototype()) {
          addMethodCandidates(
              curr.getVmClass().getDeclaredMethods(), true, memberLookupMode, receiverNode);
        }
      }
      // if we resolved to implicit `this` at parse time, try searching the lexical scope of the
      // owner too.
      for (var curr = owner; curr != null; curr = curr.getEnclosingOwner()) {
        addPropertyCandidates(curr, true, memberLookupMode, receiverNode);
        if (curr.isPrototype()) {
          addMethodCandidates(
              curr.getVmClass().getDeclaredMethods(), true, memberLookupMode, receiverNode);
        }
      }
      addPropertyCandidates(BaseModule.getModule(), false, memberLookupMode, receiverNode);
      addMethodCandidates(
          BaseModule.getModule().getVmClass().getMethods(), false, memberLookupMode, receiverNode);
    }

    for (var curr = receiver; curr != null; curr = curr.getParent()) {
      addPropertyCandidates(curr, false, memberLookupMode, receiverNode);
    }
    addMethodCandidates(receiver.getVmClass().getMethods(), false, memberLookupMode, receiverNode);

    return candidates.stream().sorted(Comparator.naturalOrder()).collect(Collectors.toList());
  }

  private void addPropertyCandidates(
      VmObjectLike object,
      boolean includeLocal,
      MemberLookupMode memberLookupMode,
      @Nullable PklNode receiverNode) {
    if (!memberKinds.contains(Kind.PROPERTY)) return;

    for (var member : EconomicMaps.getValues(object.getMembers())) {
      addIfSimilar(
          member, Candidate.Kind.PROPERTY, -1, includeLocal, memberLookupMode, receiverNode);
    }
  }

  private void addMethodCandidates(
      Iterable<ClassMethod> methods,
      boolean includeLocal,
      MemberLookupMode memberLookupMode,
      @Nullable PklNode receiverNode) {
    if (!memberKinds.contains(Kind.METHOD)) return;

    for (var method : methods) {
      addIfSimilar(
          method,
          Candidate.Kind.METHOD,
          method.getParameterCount(),
          includeLocal,
          memberLookupMode,
          receiverNode);
    }
  }

  private void addIfSimilar(
      Member member,
      Kind kind,
      int arity,
      boolean includeLocal,
      MemberLookupMode memberLookupMode,
      @Nullable PklNode receiverNode) {

    var memberName = member.getNameOrNull();
    if (memberName == null) return;

    if (includeLocal || !member.isLocal()) {
      var nameSimilarity =
          STRING_SIMILARITY.similarity(memberName.toString(), this.memberName.toString());
      if (nameSimilarity >= SIMILARITY_THRESHOLD) {
        var arityDifference = Math.abs(arity - memberArity);
        var signature = member.getCallSignature();
        assert signature != null;
        var nameWithReceiver = memberName.toString();
        // `receiverNode` is not necessarily null; we use `MemberLookupMode.EXPLICIT_RECEIVER`
        // in places that are not property/method calls, e.g.
        // `org.pkl.core.ast.expression.literal.SpecializedObjectLiteralNode.checkIsValidTypedAmendment`
        if (memberLookupMode == MemberLookupMode.EXPLICIT_RECEIVER && receiverNode != null) {
          nameWithReceiver =
              receiverNode.getSourceSection().getCharacters().toString() + "." + memberName;
        } else if (memberLookupMode == MemberLookupMode.EXPLICIT_SUPER) {
          nameWithReceiver = "super." + memberName;
        }
        if (nameSimilarity < 1 || memberArity == 0) {
          candidates.add(
              new Candidate(
                  kind, memberName, nameWithReceiver, signature, nameSimilarity, arityDifference));
        } else if (nameSimilarity == 1 && memberArity >= 0) {
          candidates.add(
              new Candidate(
                  kind,
                  memberName,
                  nameWithReceiver,
                  signature + ".apply(...)",
                  nameSimilarity,
                  arityDifference));
        }
      }
    }
  }

  public record Candidate(
      Kind kind,
      Identifier memberName,
      String memberNameWithReceiver,
      String callSignature,
      double nameSimilarity,
      int arityDifference)
      implements Comparable<Candidate> {
    // note: not consistent with equals (hence cannot use TreeSet)
    @Override
    public int compareTo(Candidate other) {
      if (nameSimilarity == other.nameSimilarity) {
        return Integer.compare(arityDifference, other.arityDifference);
      }
      return Double.compare(other.nameSimilarity, nameSimilarity);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Candidate other)) return false;
      // member lookup is name rather than signature based (but distinguishes kind)
      return kind == other.kind && memberNameWithReceiver.equals(other.memberNameWithReceiver);
    }

    @Override
    public int hashCode() {
      return kind.hashCode() * 31 + memberNameWithReceiver.hashCode();
    }

    @Override
    public String toString() {
      return callSignature;
    }

    public enum Kind {
      PROPERTY,
      METHOD
    }
  }
}
