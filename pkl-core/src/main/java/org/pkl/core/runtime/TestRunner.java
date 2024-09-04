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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.pkl.core.BufferedLogger;
import org.pkl.core.StackFrameTransformer;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.module.ModuleKeys;
import org.pkl.core.runtime.TestResults.TestSectionResults;
import org.pkl.core.runtime.TestResults.TestSectionResults.Error;
import org.pkl.core.runtime.TestResults.TestSectionResults.Failure;
import org.pkl.core.stdlib.PklConverter;
import org.pkl.core.stdlib.base.PcfRenderer;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.MutableBoolean;
import org.pkl.core.util.MutableReference;

/** Runs test results examples and facts. */
public final class TestRunner {
  private static final PklConverter converter = new PklConverter(VmMapping.empty());
  private final boolean overwrite;
  private final StackFrameTransformer stackFrameTransformer;
  private final BufferedLogger logger;

  public TestRunner(
      BufferedLogger logger, StackFrameTransformer stackFrameTransformer, boolean overwrite) {
    this.logger = logger;
    this.stackFrameTransformer = stackFrameTransformer;
    this.overwrite = overwrite;
  }

  public TestResults run(VmTyped testModule) {
    var info = VmUtils.getModuleInfo(testModule);
    var results = new TestResults(info.getModuleName(), getDisplayUri(info));

    try {
      checkAmendsPklTest(testModule);
    } catch (VmException v) {
      var error = new Error(v.getMessage(), v.toPklException(stackFrameTransformer));
      results.module.setError(error);
    }

    try {
      runFacts(testModule, results.facts);
    } catch (VmException v) {
      var error = new Error(v.getMessage(), v.toPklException(stackFrameTransformer));
      results.facts.setError(error);
    }

    try {
      runExamples(testModule, info, results.examples);
    } catch (VmException v) {
      var error = new Error(v.getMessage(), v.toPklException(stackFrameTransformer));
      results.examples.setError(error);
    }

    results.setErr(logger.getLogs());
    return results;
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

  private void runFacts(VmTyped testModule, TestSectionResults results) {
    var facts = VmUtils.readMember(testModule, Identifier.FACTS);
    if (facts instanceof VmNull) return;

    var factsMapping = (VmMapping) facts;
    factsMapping.forceAndIterateMemberValues(
        (groupKey, groupMember, groupValue) -> {
          var result = results.newResult(String.valueOf(groupKey));
          var groupListing = (VmListing) groupValue;
          groupListing.forceAndIterateMemberValues(
              ((factIndex, factMember, factValue) -> {
                result.countAssert();

                assert factValue instanceof Boolean;
                if (factValue == Boolean.FALSE) {
                  result.addFailure(
                      Failure.buildFactFailure(
                          getDisplayUri(factMember), factMember.getSourceSection()));
                }
                return true;
              }));
          return true;
        });
  }

  private void runExamples(VmTyped testModule, ModuleInfo info, TestSectionResults results) {
    var examples = VmUtils.readMember(testModule, Identifier.EXAMPLES);
    if (examples instanceof VmNull) return;

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
      doRunAndValidateExamples(examplesMapping, expectedOutputFile, actualOutputFile, results);
    } else {
      doRunAndWriteExamples(examplesMapping, expectedOutputFile, results);
    }
  }

  private void doRunAndValidateExamples(
      VmMapping examples,
      Path expectedOutputFile,
      Path actualOutputFile,
      TestSectionResults results) {
    var expectedExampleOutputs = loadExampleOutputs(expectedOutputFile);
    var actualExampleOutputs = new MutableReference<VmDynamic>(null);
    var allGroupsSucceeded = new MutableBoolean(true);
    examples.forceAndIterateMemberValues(
        (groupKey, groupMember, groupValue) -> {
          var testName = String.valueOf(groupKey);
          var group = (VmListing) groupValue;
          var expectedGroup =
              (VmDynamic) VmUtils.readMemberOrNull(expectedExampleOutputs, groupKey);

          if (expectedGroup == null) {
            results.newResult(
                testName,
                Failure.buildExamplePropertyMismatchFailure(
                    getDisplayUri(groupMember), String.valueOf(groupKey), true));
            return true;
          }

          if (group.getLength() != expectedGroup.getLength()) {
            results.newResult(
                testName,
                Failure.buildExampleLengthMismatchFailure(
                    getDisplayUri(groupMember),
                    String.valueOf(groupKey),
                    expectedGroup.getLength(),
                    group.getLength()));
            return true;
          }

          var groupSucceeded = new MutableBoolean(true);
          group.forceAndIterateMemberValues(
              ((exampleIndex, exampleMember, exampleValue) -> {
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

                  groupSucceeded.set(false);

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

                  var exampleName =
                      group.getLength() == 1 ? testName : testName + " #" + exampleIndex;

                  results.newResult(
                      exampleName,
                      Failure.buildExampleFailure(
                          getDisplayUri(exampleMember),
                          getDisplayUri(expectedMember),
                          expectedValuePcf,
                          getDisplayUri(actualMember),
                          exampleValuePcf));
                }

                return true;
              }));

          if (groupSucceeded.get()) {
            results.newResult(testName);
          } else {
            allGroupsSucceeded.set(false);
          }

          return true;
        });

    expectedExampleOutputs.iterateMembers(
        (groupKey, groupMember) -> {
          if (groupMember.isLocalOrExternalOrHidden()) {
            return true;
          }
          if (examples.getCachedValue(groupKey) == null) {
            allGroupsSucceeded.set(false);
            results.newResult(
                String.valueOf(groupKey),
                Failure.buildExamplePropertyMismatchFailure(
                    getDisplayUri(groupMember), String.valueOf(groupKey), false));
          }
          return true;
        });

    if (!allGroupsSucceeded.get() && actualExampleOutputs.isNull()) {
      writeExampleOutputs(actualOutputFile, examples);
    }
  }

  private void doRunAndWriteExamples(
      VmMapping examples, Path outputFile, TestSectionResults results) {
    examples.forceAndIterateMemberValues(
        (groupKey, groupMember, groupValue) -> {
          var example = results.newResult(String.valueOf(groupKey));
          example.setExampleWritten(true);
          return true;
        });
    writeExampleOutputs(outputFile, examples);
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
}
