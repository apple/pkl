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

/** Aggregate test results for a module. Used to verify test failures and generate reports. */
public final class TestResults {

  private final String module;
  private final String displayUri;
  private final List<TestResult> results = new ArrayList<>();
  private String err = "";

  public TestResults(String module, String displayUri) {
    this.module = module;
    this.displayUri = displayUri;
  }

  public String getModuleName() {
    return module;
  }

  public String getDisplayUri() {
    return displayUri;
  }

  public List<TestResult> getResults() {
    return Collections.unmodifiableList(results);
  }

  public TestResult newResult(String name) {
    var result = new TestResult(name);
    results.add(result);
    return result;
  }

  public void newResult(String name, Failure failure) {
    var result = new TestResult(name);
    result.addFailure(failure);
    results.add(result);
  }

  public int totalTests() {
    return results.size();
  }

  public int totalFailures() {
    int total = 0;
    for (var res : results) {
      total += res.getFailures().size();
    }
    return total;
  }

  public boolean failed() {
    for (var res : results) {
      if (res.isFailure()) return true;
    }
    return false;
  }

  public String getErr() {
    return err;
  }

  public void setErr(String err) {
    this.err = err;
  }

  public static class TestResult {

    private final String name;
    private final List<Failure> failures = new ArrayList<>();
    private final List<Error> errors = new ArrayList<>();
    private boolean isExampleWritten = false;

    public TestResult(String name) {
      this.name = name;
    }

    public boolean isSuccess() {
      return failures.isEmpty() && errors.isEmpty();
    }

    boolean isFailure() {
      return !isSuccess();
    }

    public String getName() {
      return name;
    }

    public boolean isExampleWritten() {
      return isExampleWritten;
    }

    public void setExampleWritten(boolean exampleWritten) {
      isExampleWritten = exampleWritten;
    }

    public List<Failure> getFailures() {
      return Collections.unmodifiableList(failures);
    }

    public void addFailure(Failure description) {
      failures.add(description);
    }

    public List<Error> getErrors() {
      return Collections.unmodifiableList(errors);
    }

    public void addError(Error err) {
      errors.add(err);
    }
  }

  public static class Failure {

    private final String kind;
    private final String rendered;

    private Failure(String kind, String rendered) {
      this.kind = kind;
      this.rendered = rendered;
    }

    public String getKind() {
      return kind;
    }

    public String getRendered() {
      return rendered;
    }

    public static Failure buildFactFailure(SourceSection sourceSection, String description) {
      return new Failure(
          "Fact Failure", sourceSection.getCharacters() + " ❌ (" + description + ")");
    }

    public static Failure buildExampleLengthMismatchFailure(
        String location, String property, int expectedLength, int actualLength) {
      String builder =
          "("
              + location
              + ")\n"
              + "Output mismatch: Expected \""
              + property
              + "\" to contain "
              + expectedLength
              + " examples, but found "
              + actualLength;
      return new Failure("Output Mismatch (Length)", builder);
    }

    public static Failure buildExamplePropertyMismatchFailure(
        String location, String property, boolean isMissingInExpected) {
      var builder = new StringBuilder();
      builder
          .append("(")
          .append(location)
          .append(")\n")
          .append("Output mismatch: \"")
          .append(property);
      if (isMissingInExpected) {
        builder.append("\" exists in actual but not in expected output");
      } else {
        builder.append("\" exists in expected but not in actual output");
      }
      return new Failure("Output Mismatch", builder.toString());
    }

    public static Failure buildExampleFailure(
        String location,
        String expectedLocation,
        String expectedValue,
        String actualLocation,
        String actualValue) {
      String builder =
          "("
              + location
              + ")\n"
              + "Expected: ("
              + expectedLocation
              + ")\n"
              + expectedValue
              + "\nActual: ("
              + actualLocation
              + ")\n"
              + actualValue;
      return new Failure("Example Failure", builder);
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
  }
}
