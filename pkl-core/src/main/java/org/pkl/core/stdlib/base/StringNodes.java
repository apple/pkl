/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.stdlib.base;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.LoopNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.pkl.core.ast.lambda.ApplyVmFunction1Node;
import org.pkl.core.ast.lambda.ApplyVmFunction1NodeGen;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.*;
import org.pkl.core.util.ByteArrayUtils;
import org.pkl.core.util.Pair;
import org.pkl.core.util.StringUtils;

@SuppressWarnings("unused")
public final class StringNodes {
  private StringNodes() {}

  public abstract static class TakeDropWhile extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    protected int findOffset(String string, VmFunction function) {
      var length = string.length();
      var offset = 0;

      while (offset < length) {
        var codePoint = codePointAt(string, offset);
        if (!applyLambdaNode.executeBoolean(function, codePoint)) break;
        offset += codePoint.length();
      }

      // not exact because one loop iteration may increase index by two
      LoopNode.reportLoopCount(this, offset);

      return offset;
    }

    // allocating a String for each unicode character iterated over is expensive, but we cannot
    // currently avoid this
    // (we don't have/want a pkl.base#Char type, and pkl.base#String is always represented as
    // java.lang.String)
    @TruffleBoundary
    private static String codePointAt(String string, int offset) {
      var ch1 = string.charAt(offset);
      if (Character.isHighSurrogate(ch1) && offset + 1 < string.length()) {
        var ch2 = string.charAt(offset + 1);
        if (Character.isLowSurrogate(ch2)) {
          return String.valueOf(new char[] {ch1, ch2});
        }
      }
      return String.valueOf(ch1);
    }
  }

  public abstract static class TakeDropLastWhile extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1Node.create();

    protected int findOffset(String string, VmFunction function) {
      var length = string.length();
      var offset = length;

      while (offset > 0) {
        var codePoint = codePointBefore(string, offset);
        if (!applyLambdaNode.executeBoolean(function, codePoint)) break;
        offset -= codePoint.length();
      }

      // not exact because one loop iteration may decrease index by two
      LoopNode.reportLoopCount(this, length - offset);

      return offset;
    }

    // allocating a String for each unicode character iterated over is expensive, but we cannot
    // currently avoid this
    // (we don't have/want a pkl.base#Char type, and pkl.base#String is always represented as
    // java.lang.String)
    @TruffleBoundary
    private static String codePointBefore(String string, int offset) {
      var ch2 = string.charAt(offset - 1);
      if (Character.isLowSurrogate(ch2) && offset - 2 >= 0) {
        var ch1 = string.charAt(offset - 2);
        if (Character.isHighSurrogate(ch1)) {
          return String.valueOf(new char[] {ch1, ch2});
        }
      }
      return String.valueOf(ch2);
    }
  }

  public abstract static class length extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected long eval(String self) {
      return self.codePointCount(0, self.length());
    }
  }

  public abstract static class lastIndex extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected long eval(String self) {
      return self.codePointCount(0, self.length()) - 1;
    }
  }

  public abstract static class isEmpty extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(String self) {
      return self.isEmpty();
    }
  }

  public abstract static class isBlank extends ExternalPropertyNode {
    @Specialization
    protected boolean eval(String self) {
      return StringUtils.isBlank(self);
    }
  }

  public abstract static class isRegex extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected boolean eval(String self) {
      try {
        Pattern.compile(self, Pattern.UNICODE_CASE);
        return true;
      } catch (PatternSyntaxException e) {
        return false;
      }
    }
  }

  public abstract static class chars extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected VmList eval(String self) {
      var builder = VmList.EMPTY.builder();
      self.codePoints().forEach(cp -> builder.add(Character.toString(cp)));
      return builder.build();
    }
  }

  public abstract static class codePoints extends ExternalPropertyNode {
    @Specialization
    @TruffleBoundary
    protected VmList eval(String self) {
      var builder = VmList.EMPTY.builder();
      self.codePoints().forEach(cp -> builder.add((long) cp));
      return builder.build();
    }
  }

  public abstract static class getOrNull extends ExternalMethod1Node {
    @TruffleBoundary
    @Specialization
    protected Object eval(String self, long index) {
      var charIndex = VmUtils.codePointOffsetToCharOffset(self, index);
      if (charIndex == -1 || charIndex == self.length()) return VmNull.withoutDefault();

      if (Character.isHighSurrogate(self.charAt(charIndex))) {
        return self.substring(charIndex, charIndex + 2);
      }
      return self.substring(charIndex, charIndex + 1);
    }
  }

  public abstract static class substring extends ExternalMethod2Node {
    @TruffleBoundary
    @Specialization
    protected String eval(String self, long start, long exclusiveEnd) {
      var charStart = VmUtils.codePointOffsetToCharOffset(self, start);
      if (charStart == -1) {
        throw exceptionBuilder()
            .evalError("charIndexOutOfRange", start, 0, self.codePointCount(0, self.length()))
            .withProgramValue("String", self)
            .build();
      }

      var charExclusiveEnd =
          VmUtils.codePointOffsetToCharOffset(self, exclusiveEnd - start, charStart);
      if (charExclusiveEnd < charStart) {
        throw exceptionBuilder()
            .evalError(
                "charIndexOutOfRange", exclusiveEnd, start, self.codePointCount(0, self.length()))
            .withProgramValue("String", self)
            .build();
      }

      return self.substring(charStart, charExclusiveEnd);
    }
  }

  public abstract static class substringOrNull extends ExternalMethod2Node {
    @TruffleBoundary
    @Specialization
    protected Object eval(String self, long start, long exclusiveEnd) {
      var charStart = VmUtils.codePointOffsetToCharOffset(self, start);
      if (charStart == -1) {
        return VmNull.withoutDefault();
      }

      var charExclusiveEnd =
          VmUtils.codePointOffsetToCharOffset(self, exclusiveEnd - start, charStart);
      if (charExclusiveEnd < charStart) {
        return VmNull.withoutDefault();
      }

      return self.substring(charStart, charExclusiveEnd);
    }
  }

  public abstract static class repeat extends ExternalMethod1Node {
    @TruffleBoundary
    @Specialization
    protected String eval(String self, long count) {
      // fail with our own exception if the resulting string exceeds length limit
      VmSafeMath.toInt32(VmSafeMath.multiply(self.length(), count));

      return self.repeat((int) count);
    }
  }

  public abstract static class contains extends ExternalMethod1Node {
    @TruffleBoundary
    @Specialization
    protected boolean eval(String self, String other) {
      return self.contains(other);
    }

    @TruffleBoundary
    @Specialization
    protected boolean eval(String self, VmRegex regex) {
      return regex.matcher(self).find();
    }
  }

  public abstract static class matches extends ExternalMethod1Node {
    @TruffleBoundary
    @Specialization
    protected boolean eval(String self, VmRegex regex) {
      return regex.matcher(self).matches();
    }
  }

  public abstract static class startsWith extends ExternalMethod1Node {
    @TruffleBoundary
    @Specialization
    protected boolean eval(String self, String pattern) {
      return self.startsWith(pattern);
    }

    @TruffleBoundary
    @Specialization
    protected boolean eval(String self, VmRegex regex) {
      return regex.matcher(self).lookingAt();
    }
  }

  public abstract static class endsWith extends ExternalMethod1Node {
    @TruffleBoundary
    @Specialization
    protected boolean eval(String self, String pattern) {
      return self.endsWith(pattern);
    }

    @TruffleBoundary
    @Specialization
    // inefficient but at least correct
    protected boolean eval(String self, VmRegex regex) {
      var matcher = regex.matcher(self);
      var end = -1;
      while (matcher.find()) {
        end = matcher.end();
      }
      return end == self.length();
    }
  }

  public abstract static class indexOf extends ExternalMethod1Node {
    @TruffleBoundary
    @Specialization
    protected long eval(String self, String pattern) {
      var charIndex = self.indexOf(pattern);
      if (charIndex == -1) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder()
            .evalError("doesNotContainLiteralMatch")
            .withProgramValue("String", self)
            .withProgramValue("Pattern", pattern)
            .build();
      }
      return self.codePointCount(0, charIndex);
    }

    @TruffleBoundary
    @Specialization
    protected long eval(String self, VmRegex regex) {
      var matcher = regex.matcher(self);
      if (!matcher.find()) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder()
            .evalError("doesNotContainRegexMatch")
            .withProgramValue("String", self)
            .withProgramValue("Pattern", regex)
            .build();
      }
      return self.codePointCount(0, matcher.start());
    }
  }

  public abstract static class indexOfOrNull extends ExternalMethod1Node {
    @TruffleBoundary
    @Specialization
    protected Object eval(String self, String pattern) {
      var charIndex = self.indexOf(pattern);
      if (charIndex == -1) {
        return VmNull.withoutDefault();
      }
      return (long) self.codePointCount(0, charIndex);
    }

    @TruffleBoundary
    @Specialization
    protected Object eval(String self, VmRegex regex) {
      var matcher = regex.matcher(self);
      if (!matcher.find()) {
        return VmNull.withoutDefault();
      }
      return (long) self.codePointCount(0, matcher.start());
    }
  }

  public abstract static class lastIndexOf extends ExternalMethod1Node {
    @TruffleBoundary
    @Specialization
    protected long eval(String self, String pattern) {
      var charIndex = self.lastIndexOf(pattern);
      if (charIndex == -1) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder()
            .evalError("doesNotContainLiteralMatch")
            .withProgramValue("String", self)
            .withProgramValue("Pattern", pattern)
            .build();
      }
      return self.codePointCount(0, charIndex);
    }

    @TruffleBoundary
    @Specialization
    // inefficient but at least correct
    protected long eval(String self, VmRegex regex) {
      var matcher = regex.matcher(self);

      if (!findLast(matcher)) {
        throw exceptionBuilder()
            .evalError("doesNotContainRegexMatch")
            .withProgramValue("String", self)
            .withProgramValue("Pattern", regex)
            .build();
      }

      return self.codePointCount(0, matcher.start());
    }
  }

  public abstract static class lastIndexOfOrNull extends ExternalMethod1Node {
    @TruffleBoundary
    @Specialization
    protected Object eval(String self, String pattern) {
      var charIndex = self.lastIndexOf(pattern);
      if (charIndex == -1) {
        return VmNull.withoutDefault();
      }
      return (long) self.codePointCount(0, charIndex);
    }

    @TruffleBoundary
    @Specialization
    // inefficient but at least correct
    protected Object eval(String self, VmRegex regex) {
      var m = regex.matcher(self);
      return findLast(m) ? (long) self.codePointCount(0, m.start()) : VmNull.withoutDefault();
    }
  }

  public abstract static class take extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(String self, long n) {
      VmUtils.checkPositive(n);
      var charIndex = VmUtils.codePointOffsetToCharOffset(self, n);
      return charIndex == -1 ? self : self.substring(0, charIndex);
    }
  }

  public abstract static class takeWhile extends TakeDropWhile {
    @Specialization
    protected String eval(String self, VmFunction function) {
      return substringUntil(self, findOffset(self, function));
    }
  }

  public abstract static class takeLast extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(String self, long n) {
      VmUtils.checkPositive(n);
      var charIndex = VmUtils.codePointOffsetFromEndToCharOffset(self, n);
      return charIndex == -1 ? self : self.substring(charIndex);
    }
  }

  public abstract static class takeLastWhile extends TakeDropLastWhile {
    @Specialization
    protected String eval(String self, VmFunction function) {
      return substringFrom(self, findOffset(self, function));
    }
  }

  public abstract static class drop extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(String self, long n) {
      VmUtils.checkPositive(n);
      var charIndex = VmUtils.codePointOffsetToCharOffset(self, n);
      return charIndex == -1 ? "" : self.substring(charIndex);
    }
  }

  public abstract static class dropWhile extends TakeDropWhile {
    @Specialization
    protected String eval(String self, VmFunction function) {
      var idx = findOffset(self, function);
      return substringFrom(self, idx);
    }
  }

  public abstract static class dropLast extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected String eval(String self, long n) {
      VmUtils.checkPositive(n);
      var charIndex = VmUtils.codePointOffsetFromEndToCharOffset(self, n);
      return charIndex == -1 ? "" : self.substring(0, charIndex);
    }
  }

  public abstract static class dropLastWhile extends TakeDropLastWhile {
    @Specialization
    protected String eval(String self, VmFunction function) {
      var idx = findOffset(self, function);
      return substringUntil(self, idx);
    }
  }

  public abstract static class replaceAll extends ExternalMethod2Node {
    @TruffleBoundary
    @Specialization
    protected String eval(String self, String pattern, String replacement) {
      return self.replace(pattern, replacement);
    }

    @TruffleBoundary
    @Specialization
    protected String eval(String self, VmRegex regex, String replacement) {
      try {
        return regex.matcher(self).replaceAll(replacement);
      } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
        throw exceptionBuilder()
            .evalError(
                "errorInRegexReplacement",
                regex.getPattern().toString(),
                replacement,
                e.getMessage())
            .build();
      }
    }
  }

  public abstract static class replaceFirst extends ExternalMethod2Node {
    @TruffleBoundary
    @Specialization
    protected String eval(String self, String pattern, String replacement) {
      int idx = self.indexOf(pattern);
      if (idx == -1) return self;
      return self.substring(0, idx) + replacement + self.substring(idx + pattern.length());
    }

    @TruffleBoundary
    @Specialization
    protected String eval(String self, VmRegex regex, String replacement) {
      try {
        return regex.matcher(self).replaceFirst(replacement);
      } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
        throw exceptionBuilder()
            .evalError("errorInRegexReplacement", regex.getPattern(), replacement, e.getMessage())
            .build();
      }
    }
  }

  public abstract static class replaceLast extends ExternalMethod2Node {
    @TruffleBoundary
    @Specialization
    protected String eval(String self, String pattern, String replacement) {
      var idx = self.lastIndexOf(pattern);
      if (idx == -1) return self;
      return self.substring(0, idx) + replacement + self.substring(idx + pattern.length());
    }

    @TruffleBoundary
    @Specialization
    // inefficient but at least correct
    protected String eval(String self, VmRegex regex, String replacement) {
      try {
        var matcher = regex.matcher(self);
        if (!findLast(matcher)) return self;
        var result = new StringBuilder();
        matcher.appendReplacement(result, replacement);
        matcher.appendTail(result);
        return result.toString();
      } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
        throw exceptionBuilder()
            .evalError("errorInRegexReplacement", regex.getPattern(), replacement, e.getMessage())
            .build();
      }
    }
  }

  public abstract static class replaceRange extends ExternalMethod3Node {
    @TruffleBoundary
    @Specialization
    protected String eval(String self, long start, long exclusiveEnd, String replacement) {
      var charStart = VmUtils.codePointOffsetToCharOffset(self, start);
      if (charStart == -1) {
        throw exceptionBuilder()
            .evalError("charIndexOutOfRange", start, 0, self.codePointCount(0, self.length()))
            .withProgramValue("String", self)
            .build();
      }

      var charExclusiveEnd =
          VmUtils.codePointOffsetToCharOffset(self, exclusiveEnd - start, charStart);
      if (charExclusiveEnd < charStart) {
        throw exceptionBuilder()
            .evalError(
                "charIndexOutOfRange", exclusiveEnd, start, self.codePointCount(0, self.length()))
            .withProgramValue("String", self)
            .build();
      }

      return self.substring(0, charStart) + replacement + self.substring(charExclusiveEnd);
    }
  }

  public abstract static class replaceFirstMapped extends ExternalMethod2Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1NodeGen.create();

    @Specialization
    @TruffleBoundary
    protected String eval(String self, String pattern, VmFunction mapper) {
      return doEval(self, patternOf(pattern).matcher(self), mapper);
    }

    @Specialization
    @TruffleBoundary
    protected String eval(String self, VmRegex regex, VmFunction mapper) {
      return doEval(self, regex.matcher(self), mapper);
    }

    private String doEval(String self, Matcher matcher, VmFunction mapper) {
      if (!matcher.find()) return self;
      return self.substring(0, matcher.start())
          + applyMapper(applyLambdaNode, matcher, mapper)
          + self.substring(matcher.end());
    }
  }

  public abstract static class replaceLastMapped extends ExternalMethod2Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1NodeGen.create();

    @Specialization
    @TruffleBoundary
    protected String eval(String self, String pattern, VmFunction mapper) {
      return doEval(self, patternOf(pattern).matcher(self), mapper);
    }

    @Specialization
    @TruffleBoundary
    protected String eval(String self, VmRegex regex, VmFunction mapper) {
      return doEval(self, regex.matcher(self), mapper);
    }

    private String doEval(String self, Matcher matcher, VmFunction mapper) {
      return !findLast(matcher)
          ? self
          : self.substring(0, matcher.start())
              + applyMapper(applyLambdaNode, matcher, mapper)
              + self.substring(matcher.end());
    }
  }

  public abstract static class replaceAllMapped extends ExternalMethod2Node {
    @Child private ApplyVmFunction1Node applyLambdaNode = ApplyVmFunction1NodeGen.create();

    @Specialization
    @TruffleBoundary
    protected String eval(String self, String pattern, VmFunction mapper) {
      return doEval(self, patternOf(pattern).matcher(self), mapper);
    }

    @Specialization
    @TruffleBoundary
    protected String eval(String self, VmRegex regex, VmFunction mapper) {
      return doEval(self, regex.matcher(self), mapper);
    }

    private String doEval(String self, Matcher matcher, VmFunction mapper) {
      if (!matcher.find()) return self;

      var buffer = new StringBuilder();

      do {
        matcher.appendReplacement(buffer, applyMapper(applyLambdaNode, matcher, mapper));
      } while (matcher.find());

      matcher.appendTail(buffer);
      return buffer.toString();
    }
  }

  public abstract static class toLowerCase extends ExternalMethod0Node {
    @TruffleBoundary
    @Specialization
    protected String eval(String self) {
      return self.toLowerCase(Locale.ROOT);
    }
  }

  public abstract static class toUpperCase extends ExternalMethod0Node {
    @TruffleBoundary
    @Specialization
    protected String eval(String self) {
      return self.toUpperCase(Locale.ROOT);
    }
  }

  public abstract static class reverse extends ExternalMethod0Node {
    @TruffleBoundary
    @Specialization
    protected String eval(String self) {
      return new StringBuilder(self).reverse().toString();
    }
  }

  public abstract static class trim extends ExternalMethod0Node {
    @TruffleBoundary
    @Specialization
    protected String eval(String self) {
      return self.strip();
    }
  }

  public abstract static class trimStart extends ExternalMethod0Node {
    @TruffleBoundary
    @Specialization
    protected String eval(String self) {
      return StringUtils.trimStart(self);
    }
  }

  public abstract static class trimEnd extends ExternalMethod0Node {
    @TruffleBoundary
    @Specialization
    protected String eval(String self) {
      return StringUtils.trimEnd(self);
    }
  }

  public abstract static class padStart extends ExternalMethod2Node {
    @TruffleBoundary
    @Specialization
    protected String eval(String self, long width, String ch) {
      var length = self.length();
      if (length >= width) return self;

      var result = new StringBuilder(VmSafeMath.toInt32(width));
      var c = ch.charAt(0);
      for (var i = 0; i < width - length; i++) {
        result.append(c);
      }
      result.append(self);
      return result.toString();
    }
  }

  public abstract static class padEnd extends ExternalMethod2Node {
    @TruffleBoundary
    @Specialization
    protected String eval(String self, long width, String ch) {
      var length = self.length();
      if (length >= width) return self;

      var result = new StringBuilder(VmSafeMath.toInt32(width));
      result.append(self);
      var c = ch.charAt(0);
      for (var i = 0; i < width - length; i++) {
        result.append(c);
      }
      return result.toString();
    }
  }

  public abstract static class split extends ExternalMethod1Node {
    @TruffleBoundary
    @Specialization
    protected VmList eval(String self, String separator) {
      var parts = self.split(Pattern.quote(separator));
      return VmList.create(parts);
    }

    @TruffleBoundary
    @Specialization
    protected VmList eval(String self, VmRegex separator) {
      return VmList.create(separator.getPattern().split(self));
    }
  }

  public abstract static class splitLimit extends ExternalMethod2Node {
    @TruffleBoundary
    @Specialization
    protected VmList eval(String self, String separator, long limit) {
      var parts = self.split(Pattern.quote(separator), (int) limit);
      return VmList.create(parts);
    }

    @TruffleBoundary
    @Specialization
    protected VmList eval(String self, VmRegex separator, long limit) {
      var parts = separator.getPattern().split(self, (int) limit);
      return VmList.create(parts);
    }
  }

  public abstract static class capitalize extends ExternalMethod0Node {
    @TruffleBoundary
    @Specialization
    protected String eval(String self) {
      if (self.isEmpty()) return "";

      var firstChar = self.codePointAt(0);
      return Character.toString(Character.toTitleCase(firstChar))
          + self.substring(Character.isBmpCodePoint(firstChar) ? 1 : 2);
    }
  }

  public abstract static class decapitalize extends ExternalMethod0Node {
    @TruffleBoundary
    @Specialization
    protected String eval(String self) {
      if (self.isEmpty()) return "";

      var firstChar = self.codePointAt(0);
      return Character.toString(firstChar).toLowerCase(Locale.ROOT)
          + self.substring(Character.isBmpCodePoint(firstChar) ? 1 : 2);
    }
  }

  public abstract static class toInt extends ExternalMethod0Node {
    @TruffleBoundary
    @Specialization
    protected long eval(String self) {
      try {
        return Long.parseLong(removeUnderlinesFromNumber(self));
      } catch (NumberFormatException e) {
        throw exceptionBuilder()
            .evalError("cannotParseStringAs", "Int")
            .withProgramValue("String", self)
            .build();
      }
    }
  }

  public abstract static class toIntOrNull extends ExternalMethod0Node {
    @TruffleBoundary
    @Specialization
    protected Object eval(String self) {
      try {
        return Long.parseLong(removeUnderlinesFromNumber(self));
      } catch (NumberFormatException e) {
        return VmNull.withoutDefault();
      }
    }
  }

  public abstract static class toFloat extends ExternalMethod0Node {
    @TruffleBoundary
    @Specialization
    protected double eval(String self) {
      try {
        return Double.parseDouble(removeUnderlinesFromNumber(self));
      } catch (NumberFormatException e) {
        throw exceptionBuilder()
            .evalError("cannotParseStringAs", "Float")
            .withProgramValue("String", self)
            .build();
      }
    }
  }

  public abstract static class toFloatOrNull extends ExternalMethod0Node {
    @TruffleBoundary
    @Specialization
    protected Object eval(String self) {
      try {
        return Double.parseDouble(removeUnderlinesFromNumber(self));
      } catch (NumberFormatException e) {
        return VmNull.withoutDefault();
      }
    }
  }

  public abstract static class toBoolean extends ExternalMethod0Node {
    @TruffleBoundary
    @Specialization
    protected boolean eval(String self) {
      if (self.equalsIgnoreCase("true")) return true;
      if (self.equalsIgnoreCase("false")) return false;

      throw exceptionBuilder()
          .evalError("cannotParseStringAs", "Boolean")
          .withProgramValue("String", self)
          .build();
    }
  }

  public abstract static class toBooleanOrNull extends ExternalMethod0Node {
    @TruffleBoundary
    @Specialization
    protected Object eval(String self) {
      if (self.equalsIgnoreCase("true")) return true;
      if (self.equalsIgnoreCase("false")) return false;
      return VmNull.withoutDefault();
    }
  }

  public abstract static class md5 extends ExternalPropertyNode {
    @TruffleBoundary
    @Specialization
    protected String eval(String self) {
      return ByteArrayUtils.md5(self.getBytes(StandardCharsets.UTF_8));
    }
  }

  public abstract static class sha1 extends ExternalPropertyNode {
    @TruffleBoundary
    @Specialization
    protected String eval(String self) {
      return ByteArrayUtils.sha1(self.getBytes(StandardCharsets.UTF_8));
    }
  }

  public abstract static class sha256 extends ExternalPropertyNode {
    @TruffleBoundary
    @Specialization
    protected String eval(String self) {
      return ByteArrayUtils.sha256(self.getBytes(StandardCharsets.UTF_8));
    }
  }

  public abstract static class sha256Int extends ExternalPropertyNode {
    @TruffleBoundary
    @Specialization
    protected long eval(String self) {
      return ByteArrayUtils.sha256Int(self.getBytes(StandardCharsets.UTF_8));
    }
  }

  public abstract static class base64 extends ExternalPropertyNode {
    @TruffleBoundary
    @Specialization
    protected String eval(String self) {
      return Base64.getEncoder().encodeToString(self.getBytes(StandardCharsets.UTF_8));
    }
  }

  public abstract static class base64Decoded extends ExternalPropertyNode {
    @TruffleBoundary
    @Specialization
    protected String eval(String self) {
      try {
        return new String(Base64.getDecoder().decode(self), StandardCharsets.UTF_8);
      } catch (IllegalArgumentException e) {
        throw exceptionBuilder()
            .adhocEvalError(e.getMessage())
            .withProgramValue("String", self)
            .withCause(e)
            .build();
      }
    }
  }

  @TruffleBoundary
  private static String substringFrom(String string, int start) {
    return string.substring(start);
  }

  @TruffleBoundary
  private static String substringUntil(String string, int end) {
    return string.substring(0, end);
  }

  private static Pattern patternOf(String regex) {
    return Pattern.compile(regex, Pattern.LITERAL | Pattern.UNICODE_CASE);
  }

  private static boolean findLast(Matcher m) {
    if (!m.find()) return false;
    MatchResult r;
    do {
      r = m.toMatchResult();
    } while (m.find());

    // Reset `m` to the last match before returning (should always be `true` at this point)
    return m.region(r.start(), r.end()).lookingAt();
  }

  /**
   * In `matcher`'s current match, apply `mapper` to the match and quote it (to prevent unintended
   * regex replacement syntax from being "double dereferenced"). `matcher` must have a current match
   * (i.e. the previous `find` returned `true`). The string returned is safe as a drop-in
   * replacement.
   */
  private static String applyMapper(
      ApplyVmFunction1Node applyNode, Matcher matcher, VmFunction mapper) {
    // -1 indicates regex match instead of group match (see comments in RegexMatchNodes)
    var regexMatch = RegexMatchFactory.create(Pair.of(matcher.toMatchResult(), -1));
    var replacement = applyNode.executeString(mapper, regexMatch);
    return Matcher.quoteReplacement(replacement);
  }

  /**
   * Removes `_` from numbers to be parsed to be compatible with how Pkl parses numbers. Will return
   * the string unmodified if it's invalid.
   */
  private static String removeUnderlinesFromNumber(String number) {
    var builder = new StringBuilder();
    var numberStart = true;
    for (var i = 0; i < number.length(); i++) {
      var c = number.charAt(i);
      if (c != '_') {
        builder.append(c);
      } else if (numberStart) return number;

      numberStart = c == '.' || c == 'e' || c == 'E';
    }

    return builder.toString();
  }
}
