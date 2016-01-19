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
package org.pkl.core.runtime;

import java.util.*;
import java.util.stream.Collectors;
import org.pkl.core.ast.member.ClassMethod;
import org.pkl.core.ast.member.Member;
import org.pkl.core.runtime.MemberLookupSuggestions.Candidate.Kind;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.StringSimilarity;

public class MemberLookupSuggestions {
  private static final StringSimilarity STRING_SIMILARITY = new StringSimilarity();
  // 0.77 is just about low enough to consider two three-character
  // names that differ in their first character similar
  private static final double SIMILARITY_THRESHOLD = 0.77;

  private final VmObjectLike composite;
  private final Object memberName;
  // -1 for property, #arguments for function
  private final int memberArity;

  private final Set<Candidate.Kind> memberKinds;
  private final Set<Candidate> candidates = new LinkedHashSet<>();

  public MemberLookupSuggestions(
      VmObjectLike composite, Object memberName, int memberArity, Set<Candidate.Kind> memberKinds) {
    this.composite = composite;
    this.memberName = memberName;
    this.memberArity = memberArity;
    this.memberKinds = memberKinds;
  }

  // use same search order as in member lookup,
  // so that in case of members with same name,
  // (only) the correct one is suggested
  public List<Candidate> find(boolean isImplicitReceiver) {
    candidates.clear();

    if (isImplicitReceiver) {
      for (var curr = composite; curr != null; curr = curr.getEnclosingOwner()) {
        addPropertyCandidates(curr, true);
        if (curr.isPrototype()) {
          addMethodCandidates(curr.getVmClass().getDeclaredMethods(), true);
        }
      }
      addPropertyCandidates(BaseModule.getModule(), false);
      addMethodCandidates(BaseModule.getModule().getVmClass().getMethods(), false);
    }

    for (var curr = composite; curr != null; curr = curr.getParent()) {
      addPropertyCandidates(curr, false);
    }
    addMethodCandidates(composite.getVmClass().getMethods(), false);

    return candidates.stream().sorted(Comparator.naturalOrder()).collect(Collectors.toList());
  }

  private void addPropertyCandidates(VmObjectLike object, boolean includeLocal) {
    if (!memberKinds.contains(Kind.PROPERTY)) return;

    for (var member : EconomicMaps.getValues(object.getMembers())) {
      addIfSimilar(member, Candidate.Kind.PROPERTY, -1, includeLocal);
    }
  }

  private void addMethodCandidates(Iterable<ClassMethod> methods, boolean includeLocal) {
    if (!memberKinds.contains(Kind.METHOD)) return;

    for (var method : methods) {
      addIfSimilar(method, Candidate.Kind.METHOD, method.getParameterCount(), includeLocal);
    }
  }

  private void addIfSimilar(Member member, Candidate.Kind kind, int arity, boolean includeLocal) {

    var memberName = member.getNameOrNull();
    if (memberName == null) return;

    if (includeLocal || !member.isLocal()) {
      var nameSimilarity =
          STRING_SIMILARITY.similarity(memberName.toString(), this.memberName.toString());
      if (nameSimilarity >= SIMILARITY_THRESHOLD) {
        var arityDifference = Math.abs(arity - memberArity);
        var signature = member.getCallSignature();
        assert signature != null;
        if (nameSimilarity < 1 || memberArity == 0) {
          candidates.add(
              new Candidate(
                  kind, memberName.toString(), signature, nameSimilarity, arityDifference));
        } else if (nameSimilarity == 1 && memberArity >= 0) {
          candidates.add(
              new Candidate(
                  kind,
                  memberName.toString(),
                  signature + ".apply(...)",
                  nameSimilarity,
                  arityDifference));
        }
      }
    }
  }

  public static final class Candidate implements Comparable<Candidate> {
    private final Kind kind;
    private final String name;
    private final String callSignature;
    private final double nameSimilarity;
    private final int arityDifference;

    public Candidate(
        Kind kind, String name, String callSignature, double nameSimilarity, int arityDifference) {
      this.kind = kind;
      this.name = name;
      this.callSignature = callSignature;
      this.nameSimilarity = nameSimilarity;
      this.arityDifference = arityDifference;
    }

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
      if (!(obj instanceof Candidate)) return false;

      var other = (Candidate) obj;
      // member lookup is name rather than signature based (but distinguishes kind)
      return kind == other.kind && name.equals(other.name);
    }

    @Override
    public int hashCode() {
      return kind.hashCode() * 31 + name.hashCode();
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
