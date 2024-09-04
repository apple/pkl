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
package org.pkl.core.runtime;

import com.oracle.truffle.api.source.SourceSection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.pkl.core.PklException;
import org.pkl.core.runtime.TestResults.TestSectionResults.TestSection;

/** Aggregate test results for a module. Used to verify test failures and generate reports. */
public final class TestResults {
  public final String moduleName;
  public final String displayUri;
  public final TestSectionResults module = new TestSectionResults(TestSection.MODULE);
  public final TestSectionResults facts = new TestSectionResults(TestSection.FACTS);
  public final TestSectionResults examples = new TestSectionResults(TestSection.EXAMPLES);
  private String err = "";

  public TestResults(String moduleName, String displayUri) {
    this.moduleName = moduleName;
    this.displayUri = displayUri;
  }

  public int totalTests() {
    return module.totalTests() + facts.totalTests() + examples.totalTests();
  }

  public int totalFailures() {
    return module.totalFailures() + facts.totalFailures() + examples.totalFailures();
  }

  public int totalAsserts() {
    return module.totalAsserts() + facts.totalAsserts() + examples.totalAsserts();
  }

  public int totalAssertsFailed() {
    return module.totalAssertsFailed() + facts.totalAssertsFailed() + examples.totalAssertsFailed();
  }

  public boolean failed() {
    return module.failed() || facts.failed() || examples.failed();
  }

  public String getErr() {
    return err;
  }

  public void setErr(String err) {
    this.err = err;
  }

  public static class TestSectionResults {
    public final TestSection name;
    private final List<TestResult> results = new ArrayList<>();
    private Error error;

    public TestSectionResults(TestSection name) {
      this.name = name;
    }

    public void setError(Error error) {
      this.error = error;
    }

    public Error getError() {
      return error;
    }

    public boolean hasError() {
      return error != null;
    }

    public List<TestResult> getResults() {
      return Collections.unmodifiableList(results);
    }

    public TestResult newResult(String name) {
      var result = new TestResult(name, this.name == TestSection.EXAMPLES);
      results.add(result);
      return result;
    }

    public void newResult(String name, Failure failure) {
      var result = new TestResult(name, this.name == TestSection.EXAMPLES);
      result.addFailure(failure);
      results.add(result);
    }

    public int totalTests() {
      var total = results.size();
      return (hasError() ? ++total : total);
    }

    public int totalAsserts() {
      int total = 0;
      for (var res : results) {
        total += res.totalAsserts();
      }
      return (hasError() ? ++total : total);
    }

    public int totalAssertsFailed() {
      int total = 0;
      for (var res : results) {
        total += res.totalAssertsFailed();
      }
      return (hasError() ? ++total : total);
    }

    public int totalFailures() {
      int total = 0;
      for (var res : results) {
        if (res.isFailure()) total++;
      }
      return (hasError() ? ++total : total);
    }

    public boolean failed() {
      if (hasError()) return true;

      for (var res : results) {
        if (res.isFailure()) return true;
      }
      return false;
    }

    public static class TestResult {
      public final String name;
      private int totalAsserts = 0;
      private int totalAssertsFailed = 0;
      private final List<Failure> failures = new ArrayList<>();
      public final boolean isExample;
      private boolean isExampleWritten = false;

      public TestResult(String name, boolean isExample) {
        this.name = name;
        this.isExample = isExample;
      }

      public boolean isSuccess() {
        return failures.isEmpty();
      }

      public boolean isFailure() {
        return !isSuccess();
      }

      public boolean isExampleWritten() {
        return isExampleWritten;
      }

      public void setExampleWritten(boolean exampleWritten) {
        isExampleWritten = exampleWritten;
      }

      public int totalAsserts() {
        return totalAsserts;
      }

      public void countAssert() {
        totalAsserts++;
      }

      public List<Failure> getFailures() {
        return Collections.unmodifiableList(failures);
      }

      public int totalAssertsFailed() {
        return totalAssertsFailed;
      }

      public void addFailure(Failure description) {
        failures.add(description);
        totalAssertsFailed++;
      }
    }

    public static class Failure {

      private final String kind;
      private final String failure;
      private final String location;

      private Failure(String kind, String failure, String location) {
        this.kind = kind;
        this.failure = failure;
        this.location = location;
      }

      public String getKind() {
        return kind;
      }

      public String getFailure() {
        return failure;
      }

      public String getLocation() {
        return location;
      }

      public static String renderLocation(String location) {
        return "(" + location + ")";
      }

      public String getRendered() {
        String rendered;

        if (kind == "Fact Failure") {
          rendered = failure + " " + renderLocation(getLocation());
        } else {
          rendered = renderLocation(getLocation()) + "\n" + failure;
        }

        return rendered;
      }

      public static Failure buildFactFailure(String location, SourceSection sourceSection) {
        return new Failure("Fact Failure", sourceSection.getCharacters().toString(), location);
      }

      public static Failure buildExampleLengthMismatchFailure(
          String location, String property, int expectedLength, int actualLength) {
        var builder = new StringBuilder();
        builder
            .append("Output mismatch: Expected \"")
            .append(property)
            .append("\" to contain ")
            .append(expectedLength)
            .append(" examples, but found ")
            .append(actualLength);

        return new Failure("Output Mismatch (Length)", builder.toString(), location);
      }

      public static Failure buildExamplePropertyMismatchFailure(
          String location, String property, boolean isMissingInExpected) {

        String exists_in;
        String missing_in;

        if (isMissingInExpected) {
          exists_in = "actual";
          missing_in = "expected";
        } else {
          exists_in = "expected";
          missing_in = "actual";
        }

        var builder = new StringBuilder();
        builder
            .append("Output mismatch: \"")
            .append(property)
            .append("\" exists in ")
            .append(exists_in)
            .append(" but not in ")
            .append(missing_in)
            .append(" output");

        return new Failure("Output Mismatch", builder.toString(), location);
      }

      public static Failure buildExampleFailure(
          String location,
          String expectedLocation,
          String expectedValue,
          String actualLocation,
          String actualValue) {
        var builder = new StringBuilder();
        builder
            .append("Expected: ")
            .append(renderLocation(expectedLocation))
            .append("\n")
            .append(expectedValue)
            .append("\n")
            .append("Actual: ")
            .append(renderLocation(actualLocation))
            .append("\n")
            .append(actualValue);

        return new Failure("Example Failure", builder.toString(), location);
      }
    }

    public static class Error {

      private final String message;
      private final PklException exception;

      public Error(String message, PklException exception) {
        this.message = message;
        this.exception = exception;
      }

      public String getMessage() {
        return message;
      }

      public Exception getException() {
        return exception;
      }

      public String getRendered() {
        return exception.getMessage();
      }
    }

    public enum TestSection {
      MODULE("module"),
      FACTS("facts"),
      EXAMPLES("examples");

      private final String name;

      TestSection(final String name) {
        this.name = name;
      }

      @Override
      public String toString() {
        return name;
      }
    }
  }
}
