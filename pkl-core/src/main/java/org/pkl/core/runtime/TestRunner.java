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
package org.pkl.core.runtime;

import com.oracle.truffle.api.source.SourceSection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.pkl.core.BufferedLogger;
import org.pkl.core.StackFrameTransformer;
import org.pkl.core.TestResults;
import org.pkl.core.TestResults.Failure;
import org.pkl.core.TestResults.TestResult;
import org.pkl.core.TestResults.TestSectionName;
import org.pkl.core.TestResults.TestSectionResults;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.module.ModuleKeys;
import org.pkl.core.stdlib.PklConverter;
import org.pkl.core.stdlib.base.PcfRenderer;
import org.pkl.core.util.ColorTheme;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.MutableBoolean;
import org.pkl.core.util.MutableReference;

/** Runs test results examples and facts. */
public final class TestRunner {
  private static final PklConverter converter = new PklConverter(VmMapping.empty());
  private final BufferedLogger logger;
  private final StackFrameTransformer stackFrameTransformer;
  private final boolean overwrite;
  private final boolean useColor;

  public TestRunner(
      BufferedLogger logger,
      StackFrameTransformer stackFrameTransformer,
      boolean overwrite,
      boolean useColor) {
    this.logger = logger;
    this.stackFrameTransformer = stackFrameTransformer;
    this.overwrite = overwrite;
    this.useColor = useColor;
  }

  public TestResults run(VmTyped testModule) {
    var info = VmUtils.getModuleInfo(testModule);
    var resultsBuilder = new TestResults.Builder(info.getModuleName(), getDisplayUri(info));

    try {
      checkAmendsPklTest(testModule);
    } catch (VmException v) {
      var error =
          new TestResults.Error(v.getMessage(), v.toPklException(stackFrameTransformer, useColor));
      return resultsBuilder.setError(error).build();
    }

    resultsBuilder.setFactsSection(runFacts(testModule));
    resultsBuilder.setExamplesSection(runExamples(testModule, info));

    resultsBuilder.setStdErr(logger.getLogs());
    return resultsBuilder.build();
  }

  private void checkAmendsPklTest(VmTyped value) {
    var testModuleClass = TestModule.getModule().getVmClass();
    var moduleClass = value.getVmClass();
    while (moduleClass != testModuleClass) {
      moduleClass = moduleClass.getSuperclass();
      if (moduleClass == null) {
        throw new VmExceptionBuilder().typeMismatch(value, testModuleClass).build();
      }
    }
  }

  private TestSectionResults runFacts(VmTyped testModule) {
    var facts = VmUtils.readMember(testModule, Identifier.FACTS);
    if (facts instanceof VmNull) return new TestSectionResults(TestSectionName.FACTS, List.of());

    var testResults = new ArrayList<TestResult>();
    var factsMapping = (VmMapping) facts;
    factsMapping.forceAndIterateMemberValues(
        (groupKey, groupMember, groupValue) -> {
          var listing = (VmListing) groupValue;
          var name = String.valueOf(groupKey);
          var resultBuilder = new TestResult.Builder(name);
          listing.iterateMembers(
              (idx, member) -> {
                if (member.isLocalOrExternalOrHidden()) {
                  return true;
                }
                try {
                  var factValue = VmUtils.readMember(listing, idx);
                  if (factValue == Boolean.FALSE) {
                    var failure = factFailure(member.getSourceSection(), getDisplayUri(member));
                    resultBuilder.addFailure(failure);
                  } else {
                    resultBuilder.addSuccess();
                  }
                } catch (VmException err) {
                  var error =
                      new TestResults.Error(
                          err.getMessage(), err.toPklException(stackFrameTransformer, useColor));
                  resultBuilder.addError(error);
                }
                return true;
              });
          testResults.add(resultBuilder.build());
          return true;
        });
    return new TestSectionResults(TestSectionName.FACTS, Collections.unmodifiableList(testResults));
  }

  private TestSectionResults runExamples(VmTyped testModule, ModuleInfo info) {
    var examples = VmUtils.readMember(testModule, Identifier.EXAMPLES);
    if (examples instanceof VmNull)
      return new TestSectionResults(TestSectionName.EXAMPLES, List.of());

    var moduleUri = info.getModuleKey().getUri();
    if (!moduleUri.getScheme().equalsIgnoreCase("file")) {
      throw new VmExceptionBuilder()
          .evalError("cannotEvaluateNonFileBasedTestModule", moduleUri)
          .build();
    }

    var examplesMapping = (VmMapping) examples;
    var moduleFile = Path.of(moduleUri);
    var expectedOutputFile = moduleFile.resolveSibling(moduleFile.getFileName() + "-expected.pcf");
    var actualOutputFile = moduleFile.resolveSibling(moduleFile.getFileName() + "-actual.pcf");

    try {
      Files.deleteIfExists(actualOutputFile);
    } catch (IOException e) {
      throw new VmExceptionBuilder()
          .evalError("ioErrorWritingTestOutputFile", actualOutputFile)
          .withCause(e)
          .build();
    }
    try {
      if (overwrite) {
        Files.deleteIfExists(expectedOutputFile);
      }
    } catch (IOException e) {
      throw new VmExceptionBuilder()
          .evalError("ioErrorWritingTestOutputFile", expectedOutputFile)
          .withCause(e)
          .build();
    }

    if (Files.exists(expectedOutputFile)) {
      return doRunAndValidateExamples(examplesMapping, expectedOutputFile, actualOutputFile);
    } else {
      return doRunAndWriteExamples(examplesMapping, expectedOutputFile);
    }
  }

  private TestSectionResults doRunAndValidateExamples(
      VmMapping examples, Path expectedOutputFile, Path actualOutputFile) {
    var expectedExampleOutputs = loadExampleOutputs(expectedOutputFile);
    var actualExampleOutputs = new MutableReference<VmDynamic>(null);
    var allGroupsSucceeded = new MutableBoolean(true);
    var errored = new MutableBoolean(false);
    var testResults = new ArrayList<TestResult>();
    examples.forceAndIterateMemberValues(
        (groupKey, groupMember, groupValue) -> {
          var testName = String.valueOf(groupKey);
          var group = (VmListing) groupValue;
          var expectedGroup =
              (VmDynamic) VmUtils.readMemberOrNull(expectedExampleOutputs, groupKey);
          var testResultBuilder = new TestResult.Builder(testName);

          if (expectedGroup == null) {
            testResultBuilder.addFailure(
                examplePropertyMismatchFailure(getDisplayUri(groupMember), testName, true));
            testResults.add(testResultBuilder.build());
            return true;
          }

          if (group.getLength() != expectedGroup.getLength()) {
            testResultBuilder.addFailure(
                exampleLengthMismatchFailure(
                    getDisplayUri(groupMember),
                    testName,
                    expectedGroup.getLength(),
                    group.getLength()));
            testResults.add(testResultBuilder.build());
            return true;
          }

          group.iterateMembers(
              ((exampleIndex, exampleMember) -> {
                if (exampleMember.isLocalOrExternalOrHidden()) {
                  return true;
                }

                Object exampleValue;
                try {
                  exampleValue = VmUtils.readMember(group, exampleIndex);
                } catch (VmException err) {
                  errored.set(true);
                  testResultBuilder.addError(
                      new TestResults.Error(
                          err.getMessage(), err.toPklException(stackFrameTransformer, useColor)));
                  return true;
                }
                var expectedValue = VmUtils.readMember(expectedGroup, exampleIndex);

                var exampleValuePcf = renderAsPcf(exampleValue);
                var expectedValuePcf = renderAsPcf(expectedValue);

                if (!(exampleValuePcf.equals(expectedValuePcf))) {
                  if (actualExampleOutputs.isNull()) {
                    // immediately write and load `<fileName>-actual.pcf`
                    // so that we can generate deep link with correct line number for each
                    // mismatch
                    writeExampleOutputs(actualOutputFile, examples);
                    actualExampleOutputs.set(loadExampleOutputs(actualOutputFile));
                  }

                  var expectedMember = VmUtils.findMember(expectedGroup, exampleIndex);
                  assert expectedMember != null;

                  var actualGroup =
                      (VmObjectLike) VmUtils.readMemberOrNull(actualExampleOutputs.get(), groupKey);
                  var actualMember =
                      actualGroup == null ? null : VmUtils.findMember(actualGroup, exampleIndex);
                  if (actualMember == null) {
                    // file was written earlier in this method;
                    // must have been tampered with by another process
                    throw new VmExceptionBuilder()
                        .evalError("invalidOutputFileStructure", actualOutputFile)
                        .build();
                  }

                  testResultBuilder.addFailure(
                      exampleFailure(
                          getDisplayUri(exampleMember),
                          getDisplayUri(expectedMember),
                          expectedValuePcf,
                          getDisplayUri(actualMember),
                          exampleValuePcf,
                          testResultBuilder.getCount()));
                } else {
                  testResultBuilder.addSuccess();
                }

                return true;
              }));

          testResults.add(testResultBuilder.build());

          return true;
        });

    expectedExampleOutputs.iterateMembers(
        (groupKey, groupMember) -> {
          if (groupMember.isLocalOrExternalOrHidden()) {
            return true;
          }
          if (examples.getCachedValue(groupKey) == null) {
            var testName = String.valueOf(groupKey);
            allGroupsSucceeded.set(false);
            var result =
                new TestResult.Builder(testName)
                    .addFailure(
                        examplePropertyMismatchFailure(getDisplayUri(groupMember), testName, false))
                    .build();
            testResults.add(result);
          }
          return true;
        });

    if (!allGroupsSucceeded.get() && actualExampleOutputs.isNull() && !errored.get()) {
      writeExampleOutputs(actualOutputFile, examples);
    }
    return new TestSectionResults(
        TestSectionName.EXAMPLES, Collections.unmodifiableList(testResults));
  }

  private TestSectionResults doRunAndWriteExamples(VmMapping examples, Path outputFile) {
    var testResults = new ArrayList<TestResult>();
    var allSucceeded = new MutableBoolean(true);
    examples.forceAndIterateMemberValues(
        (groupKey, groupMember, groupValue) -> {
          var testName = String.valueOf(groupKey);
          var listing = (VmListing) groupValue;
          var testResultBuilder = new TestResult.Builder(testName);
          var success = new MutableBoolean(true);
          listing.iterateMembers(
              (idx, member) -> {
                if (member.isLocalOrExternalOrHidden()) {
                  return true;
                }
                try {
                  VmUtils.readMember(listing, idx);
                  return true;
                } catch (VmException err) {
                  testResultBuilder.addError(
                      new TestResults.Error(
                          err.getMessage(), err.toPklException(stackFrameTransformer, useColor)));
                  allSucceeded.set(false);
                  success.set(false);
                  return true;
                }
              });
          if (success.get()) {
            // treat writing an example as a message
            testResultBuilder.setExampleWritten(true);
            testResultBuilder.addFailure(
                writtenExampleOutputFailure(testName, getDisplayUri(groupMember)));
          }
          testResults.add(testResultBuilder.build());
          return true;
        });
    if (allSucceeded.get()) {
      writeExampleOutputs(outputFile, examples);
    }
    return new TestSectionResults(
        TestSectionName.EXAMPLES, Collections.unmodifiableList(testResults));
  }

  private void writeExampleOutputs(Path outputFile, VmMapping examples) {
    var outputFileContent =
        new VmDynamic(
            VmUtils.createEmptyMaterializedFrame(),
            BaseModule.getDynamicClass().getPrototype(),
            EconomicMaps.of(
                Identifier.EXAMPLES,
                VmUtils.createSyntheticObjectProperty(Identifier.EXAMPLES, "examples", examples)),
            0);
    var builder = new StringBuilder();
    new PcfRenderer(builder, "  ", converter, false, true).renderDocument(outputFileContent);
    try {
      Files.writeString(outputFile, builder);
    } catch (IOException e) {
      throw new VmExceptionBuilder()
          .evalError("ioErrorWritingTestOutputFile", outputFile)
          .withCause(e)
          .build();
    }
  }

  private VmDynamic loadExampleOutputs(Path outputFile) {
    // load file manually to prevent it from being cached (won't need it again)
    String fileContent;
    try {
      fileContent = Files.readString(outputFile, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new VmExceptionBuilder()
          .evalError("ioErrorReadingTestOutputFile", outputFile)
          .withCause(e)
          .build();
    }
    var module =
        VmLanguage.get(null).loadModule(ModuleKeys.synthetic(outputFile.toUri(), fileContent));
    var examples = (VmDynamic) VmUtils.readMemberOrNull(module, Identifier.EXAMPLES);
    if (examples == null) {
      throw new VmExceptionBuilder().evalError("invalidOutputFileStructure", outputFile).build();
    }
    return examples;
  }

  private static String renderAsPcf(Object pklValue) {
    var builder = new StringBuilder();
    new PcfRenderer(builder, "  ", converter, false, false).renderValue(pklValue);
    if (pklValue instanceof VmObject) {
      builder.insert(0, "new ");
    }
    return builder.toString();
  }

  private static String getDisplayUri(ObjectMember member) {
    return VmUtils.getDisplayUri(
        member.getSourceSection(), VmContext.get(null).getFrameTransformer());
  }

  private static String getDisplayUri(ModuleInfo moduleInfo) {
    return VmUtils.getDisplayUri(
        moduleInfo.getModuleKey().getUri(), VmContext.get(null).getFrameTransformer());
  }

  private Failure factFailure(SourceSection sourceSection, String location) {
    var sb = new AnsiCodingStringBuilder(useColor);
    sb.append(ColorTheme.TEST_FACT_SOURCE, sourceSection.getCharacters().toString()).append(" ");
    appendLocation(sb, location);
    return new Failure("Fact Failure", sb.toString());
  }

  private Failure exampleLengthMismatchFailure(
      String location, String property, int expectedLength, int actualLength) {
    var sb = new AnsiCodingStringBuilder(useColor);
    appendLocation(sb, location);

    sb.append('\n')
        .append(
            ColorTheme.TEST_FAILURE_MESSAGE,
            () ->
                sb.append("Output mismatch: Expected \"")
                    .append(property)
                    .append("\" to contain ")
                    .append(expectedLength)
                    .append(" examples, but found ")
                    .append(actualLength));
    return new Failure("Output Mismatch (Length)", sb.toString());
  }

  private Failure examplePropertyMismatchFailure(
      String location, String property, boolean isMissingInExpected) {

    String existsIn;
    String missingIn;

    if (isMissingInExpected) {
      existsIn = "actual";
      missingIn = "expected";
    } else {
      existsIn = "expected";
      missingIn = "actual";
    }

    var sb = new AnsiCodingStringBuilder(useColor);
    appendLocation(sb, location);

    sb.append('\n')
        .append(
            ColorTheme.TEST_FAILURE_MESSAGE,
            () ->
                sb.append("Output mismatch: \"")
                    .append(property)
                    .append("\" exists in ")
                    .append(existsIn)
                    .append(" but not in ")
                    .append(missingIn)
                    .append(" output"));
    return new Failure("Output Mismatch", sb.toString());
  }

  private Failure exampleFailure(
      String location,
      String expectedLocation,
      String expectedValue,
      String actualLocation,
      String actualValue,
      int exampleNumber) {
    var sb = new AnsiCodingStringBuilder(useColor);
    sb.append(ColorTheme.TEST_NAME, "#" + exampleNumber + ": ");
    sb.append(
        ColorTheme.TEST_FAILURE_MESSAGE,
        () -> {
          appendLocation(sb, location);
          sb.append("\n  Expected: ");
          appendLocation(sb, expectedLocation);
          sb.append("\n  ");
          sb.append(ColorTheme.TEST_EXAMPLE_OUTPUT, expectedValue.replaceAll("\n", "\n  "));
          sb.append("\n  Actual: ");
          appendLocation(sb, actualLocation);
          sb.append("\n  ");
          sb.append(ColorTheme.TEST_EXAMPLE_OUTPUT, actualValue.replaceAll("\n", "\n  "));
        });
    return new Failure("Example Failure", sb.toString());
  }

  private void appendLocation(AnsiCodingStringBuilder stringBuilder, String location) {
    stringBuilder.append(
        ColorTheme.STACK_FRAME,
        () -> stringBuilder.append("(").appendUntrusted(location).append(")"));
  }

  private Failure writtenExampleOutputFailure(String testName, String location) {
    var sb = new AnsiCodingStringBuilder(useColor);
    appendLocation(sb, location);
    sb.append(ColorTheme.TEST_FAILURE_MESSAGE, "\nWrote expected output for test ").append(testName);
    return new Failure("Example Output Written", sb.toString());
  }
}
