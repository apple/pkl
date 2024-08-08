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
import org.pkl.core.runtime.TestResults.Error;
import org.pkl.core.runtime.TestResults.Failure;
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
      runFacts(testModule, results);
      runExamples(testModule, info, results);
    } catch (VmException v) {
      var meta = results.newResult(info.getModuleName());
      meta.addError(new Error(v.getMessage(), v.toPklException(stackFrameTransformer)));
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

  private void runFacts(VmTyped testModule, TestResults results) {
    var facts = VmUtils.readMember(testModule, Identifier.FACTS);
    if (facts instanceof VmNull) return;

    var factsMapping = (VmMapping) facts;
    factsMapping.forceAndIterateMemberValues(
        (groupKey, groupMember, groupValue) -> {
          var listing = (VmListing) groupValue;
          var result = results.newResult(String.valueOf(groupKey));
          return listing.iterateMembers(
              (idx, member) -> {
                if (member.isLocalOrExternalOrHidden()) {
                  return true;
                }
                try {
                  var factValue = VmUtils.readMember(listing, idx);
                  if (factValue == Boolean.FALSE) {
                    result.addFailure(
                        Failure.buildFactFailure(member.getSourceSection(), getDisplayUri(member)));
                  }
                } catch (VmException err) {
                  result.addError(
                      new Error(err.getMessage(), err.toPklException(stackFrameTransformer)));
                }
                return true;
              });
        });
  }

  private void runExamples(VmTyped testModule, ModuleInfo info, TestResults results) {
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
      VmMapping examples, Path expectedOutputFile, Path actualOutputFile, TestResults results) {
    var expectedExampleOutputs = loadExampleOutputs(expectedOutputFile);
    var actualExampleOutputs = new MutableReference<VmDynamic>(null);
    var allGroupsSucceeded = new MutableBoolean(true);
    var errored = new MutableBoolean(false);
    examples.forceAndIterateMemberValues(
        (groupKey, groupMember, groupValue) -> {
          var testName = String.valueOf(groupKey);
          var group = (VmListing) groupValue;
          var expectedGroup =
              (VmDynamic) VmUtils.readMemberOrNull(expectedExampleOutputs, groupKey);
          var result = results.newResult(testName);

          if (expectedGroup == null) {
            results.newResult(
                testName,
                Failure.buildExamplePropertyMismatchFailure(
                    getDisplayUri(groupMember), String.valueOf(groupKey), true));
            return true;
          }

          if (group.getLength() != expectedGroup.getLength()) {
            result.addFailure(
                Failure.buildExampleLengthMismatchFailure(
                    getDisplayUri(groupMember),
                    String.valueOf(groupKey),
                    expectedGroup.getLength(),
                    group.getLength()));
            return true;
          }

          var groupSucceeded = new MutableBoolean(true);
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
                  result.addError(
                      new Error(err.getMessage(), err.toPklException(stackFrameTransformer)));
                  groupSucceeded.set(false);
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

                  result.addFailure(
                      Failure.buildExampleFailure(
                          getDisplayUri(exampleMember),
                          getDisplayUri(expectedMember),
                          expectedValuePcf,
                          getDisplayUri(actualMember),
                          exampleValuePcf));
                }

                return true;
              }));

          if (!groupSucceeded.get()) {
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
            results
                .newResult(String.valueOf(groupKey))
                .addFailure(
                    Failure.buildExamplePropertyMismatchFailure(
                        getDisplayUri(groupMember), String.valueOf(groupKey), false));
          }
          return true;
        });

    if (!allGroupsSucceeded.get() && actualExampleOutputs.isNull() && !errored.get()) {
      writeExampleOutputs(actualOutputFile, examples);
    }
  }

  private void doRunAndWriteExamples(VmMapping examples, Path outputFile, TestResults results) {
    var allSucceeded =
        examples.forceAndIterateMemberValues(
            (groupKey, groupMember, groupValue) -> {
              var listing = (VmListing) groupValue;
              var success =
                  listing.iterateMembers(
                      (idx, member) -> {
                        if (member.isLocalOrExternalOrHidden()) {
                          return true;
                        }
                        try {
                          VmUtils.readMember(listing, idx);
                          return true;
                        } catch (VmException err) {
                          results
                              .newResult(String.valueOf(groupKey))
                              .addError(
                                  new Error(
                                      err.getMessage(), err.toPklException(stackFrameTransformer)));
                          return false;
                        }
                      });
              if (!success) {
                return false;
              }
              results.newResult(String.valueOf(groupKey)).setExampleWritten(true);
              return true;
            });
    if (allSucceeded) {
      writeExampleOutputs(outputFile, examples);
    }
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
