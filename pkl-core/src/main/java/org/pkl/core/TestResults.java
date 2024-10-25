/*
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
package org.pkl.core;

import java.util.ArrayList;
import java.util.List;
import org.pkl.core.util.Nullable;

/**
 * The results of testing a Pkl test module.
 *
 * <p>A test module is a module that amends {@code pkl:test}, and is evaluated with {@link
 * Evaluator#evaluateTest(ModuleSource, boolean)}.
 *
 * <p>Test results have two sections; facts, and examples. Each section has multiple individual
 * results, which themselves contain individual assertions.
 *
 * @since 0.27.0
 * @param moduleName The name of the module that was tested.
 * @param displayUri A location URI formatted to be displayed.
 * @param facts The result of testing facts.
 * @param examples The result of testing examples.
 * @param logs The log output resulting from running the test.
 * @param error An error that arose from evaluating the test module itself.
 *     <p>If non-null, {@code facts} and {@code examples} are guaranteed to have 0 results.
 */
@SuppressWarnings("UnusedReturnValue")
public record TestResults(
    String moduleName,
    String displayUri,
    TestSectionResults facts,
    TestSectionResults examples,
    String logs,
    @Nullable Error error) {

  /** The total number of tests between facts and examples. */
  public int totalTests() {
    return facts.totalTests() + examples.totalTests();
  }

  /**
   * The total number of failed {@linkplain TestResult test results}.
   *
   * <p>A {@link TestResult} has failed if any of its assertions have a {@link Failure} or an {@link
   * Error}.
   */
  public int totalFailures() {
    return facts.totalFailures() + examples.totalFailures();
  }

  /** The total number of assertions between facts and examples. */
  public int totalAsserts() {
    return facts.totalAsserts() + examples.totalAsserts();
  }

  /**
   * The total number of individual assertions that have failed.
   *
   * <p>A single test can have multiple failed assertions.
   */
  public int totalAssertsFailed() {
    return facts.totalAssertsFailed() + examples.totalAssertsFailed();
  }

  /**
   * Whether the test in aggregate has failed or not.
   *
   * <p>An individual {@link TestResult} has failed if any of its assertions have a {@link Failure}
   * or an {@link Error}.
   */
  public boolean failed() {
    return error != null || facts.failed() || examples.failed();
  }

  public static class Builder {

    private final String moduleName;
    private final String displayUri;
    private TestSectionResults factsSection =
        new TestSectionResults(TestSectionName.FACTS, List.of());
    private TestSectionResults examplesSection =
        new TestSectionResults(TestSectionName.EXAMPLES, List.of());
    private String stdErr = "";
    private @Nullable Error error = null;

    public Builder(String moduleName, String displayUri) {
      this.moduleName = moduleName;
      this.displayUri = displayUri;
    }

    public Builder setError(Error error) {
      this.error = error;
      return this;
    }

    public Builder setStdErr(String stdErr) {
      this.stdErr = stdErr;
      return this;
    }

    public Builder setFactsSection(TestSectionResults factsSection) {
      assert factsSection.name() == TestSectionName.FACTS;
      this.factsSection = factsSection;
      return this;
    }

    public Builder setExamplesSection(TestSectionResults examplesSection) {
      assert examplesSection.name() == TestSectionName.EXAMPLES;
      this.examplesSection = examplesSection;
      return this;
    }

    public TestResults build() {
      return new TestResults(moduleName, displayUri, factsSection, examplesSection, stdErr, error);
    }
  }

  public enum TestSectionName {
    FACTS("facts"),
    EXAMPLES("examples");

    private final String name;

    TestSectionName(final String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  /**
   * The results of either facts or examples.
   *
   * @param name The name of this section
   * @param results The results of the individual tests in this section.
   */
  public record TestSectionResults(TestSectionName name, List<TestResult> results) {
    public int totalTests() {
      return results.size();
    }

    public int totalAsserts() {
      var total = 0;
      for (var res : results) {
        total += res.totalAsserts();
      }
      return total;
    }

    public int totalAssertsFailed() {
      var total = 0;
      for (var res : results) {
        total += res.totalAssertsFailed();
      }
      return total;
    }

    public int totalFailures() {
      var total = 0;
      for (var res : results) {
        if (res.isFailure()) {
          total++;
        }
      }
      return total;
    }

    public boolean failed() {
      for (var res : results) {
        if (res.isFailure()) {
          return true;
        }
      }
      return false;
    }

    public boolean hasError() {
      for (var res : results) {
        if (!res.errors().isEmpty()) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * The result of a single test.
   *
   * <p>A test can have multiple assertions, where the assertion itself can have failed, or have
   * thrown an exception.
   *
   * @param name The name of the test.
   * @param totalAsserts The total number of assertions in the test.
   * @param failures The number of assertion failures in the test.
   * @param errors The number of errors that were thrown when the test was run.
   * @param isExampleWritten Whether the test is considered as having its example written or not.
   */
  public record TestResult(
      String name,
      int totalAsserts,
      List<Failure> failures,
      List<Error> errors,
      boolean isExampleWritten) {
    public int totalAssertsFailed() {
      return failures.size() + errors.size();
    }

    public boolean isFailure() {
      return totalAssertsFailed() > 0;
    }

    public static class Builder {

      private final String name;
      private final List<Failure> failures = new ArrayList<>();
      private final List<Error> errors = new ArrayList<>();
      private int successes = 0;
      private boolean isExampleWritten;

      public Builder(String name) {
        this.name = name;
      }

      public Builder addSuccess() {
        this.successes++;
        return this;
      }

      public Builder addFailure(Failure failure) {
        this.failures.add(failure);
        return this;
      }

      public Builder addError(Error error) {
        this.errors.add(error);
        return this;
      }

      public Builder setExampleWritten(boolean exampleWritten) {
        this.isExampleWritten = exampleWritten;
        return this;
      }

      public TestResult build() {
        return new TestResult(
            name, failures.size() + errors.size() + successes, failures, errors, isExampleWritten);
      }
    }
  }

  public record Error(String message, PklException exception) {
    public String render() {
      return exception.getMessage();
    }
  }

  public record Failure(String kind, String failure, String location) {

    public String render() {
      if (kind.equals("Fact Failure")) {
        return failure + " " + renderLocation(location);
      } else {
        return renderLocation(location) + "\n" + failure;
      }
    }

    private static String renderLocation(String location) {
      return "(" + location + ")";
    }
  }
}
