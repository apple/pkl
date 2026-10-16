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
package org.pkl.core.stdlib.test.report;

import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.pkl.core.TestResults;
import org.pkl.core.TestResults.Error;
import org.pkl.core.TestResults.TestResult;
import org.pkl.core.TestResults.TestSectionResults;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmDynamic;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmObjectBuilder;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.XmlModule;
import org.pkl.core.stdlib.PklConverter;
import org.pkl.core.stdlib.xml.RendererNodes.Renderer;

public final class JUnitReporter implements TestReporter {

  private final String aggregateSuiteName;

  public JUnitReporter(String aggregateSuiteName) {
    this.aggregateSuiteName = aggregateSuiteName;
  }

  public JUnitReporter() {
    this("");
  }

  @Override
  public void report(TestResults results, Writer writer) throws IOException {
    writer.append(renderXML("    ", "1.0", buildSuite(results)));
  }

  @Override
  public void summarize(List<TestResults> allTestResults, Writer writer) throws IOException {
    var totalTests = allTestResults.stream().mapToLong(TestResults::totalTests).sum();
    var totalFailures = allTestResults.stream().mapToLong(TestResults::totalFailures).sum();

    var attrs =
        buildAttributes(
            "name", aggregateSuiteName,
            "tests", totalTests,
            "failures", totalFailures);

    var suite =
        buildXmlElement(
            "testsuites",
            attrs,
            allTestResults.stream().map(this::buildSuite).toArray(VmDynamic[]::new));

    writer.append(renderXML("    ", "1.0", suite));
  }

  private VmDynamic buildSuite(TestResults results) {
    if (results.error() != null) {
      var testCase = rootTestCase(results, results.error());
      var attrs =
          buildAttributes(
              "name", results.moduleName(),
              "tests", 1,
              "failures", 1);
      return buildXmlElement("testsuite", attrs, testCase);
    }

    var testCases = testCases(results.moduleName(), results.facts());
    testCases.addAll(testCases(results.moduleName(), results.examples()));

    if (!results.logs().isBlank()) {
      var err =
          buildXmlElement(
              "system-err",
              VmMapping.empty(),
              builder -> builder.addElement(makeCdata(results.logs())));
      testCases.add(err);
    }

    var attrs =
        buildAttributes(
            "name", results.moduleName(),
            "tests", (long) results.totalTests(),
            "failures", (long) results.totalFailures());

    return buildXmlElement("testsuite", attrs, testCases.toArray(new VmDynamic[0]));
  }

  private VmDynamic rootTestCase(TestResults results, TestResults.Error error) {
    var testCaseAttrs =
        buildAttributes("classname", results.moduleName(), "name", results.moduleName());
    var err = error(error);
    return buildXmlElement("testcase", testCaseAttrs, err.toArray(new VmDynamic[0]));
  }

  private ArrayList<VmDynamic> testCases(String moduleName, TestSectionResults testSectionResults) {
    var elements = new ArrayList<VmDynamic>(testSectionResults.totalTests());

    for (var res : testSectionResults.results()) {
      var attrs =
          buildAttributes(
              "classname", moduleName + "." + testSectionResults.name(), "name", res.name());
      var failures = failures(res);
      failures.addAll(errors(res));
      var element = buildXmlElement("testcase", attrs, failures.toArray(new VmDynamic[0]));
      elements.add(element);
    }
    return elements;
  }

  private ArrayList<VmDynamic> failures(TestResult res) {
    var list = new ArrayList<VmDynamic>();
    for (var fail : res.failures()) {
      var attrs = buildAttributes("message", fail.kind());
      list.add(
          buildXmlElement(
              "failure", attrs, builder -> builder.addElement(stripColors(fail.message()))));
    }
    return list;
  }

  private ArrayList<VmDynamic> errors(TestResult res) {
    var list = new ArrayList<VmDynamic>();
    for (var error : res.errors()) {
      var attrs = buildAttributes("message", error.message());
      list.add(
          buildXmlElement(
              "error",
              attrs,
              builder -> builder.addElement(stripColors(error.exception().getMessage()))));
    }
    return list;
  }

  private ArrayList<VmDynamic> error(Error error) {
    var list = new ArrayList<VmDynamic>();
    var attrs = buildAttributes("message", error.message());
    list.add(
        buildXmlElement(
            "error",
            attrs,
            builder -> builder.addElement(stripColors("\n" + error.exception().getMessage()))));
    return list;
  }

  private VmDynamic buildXmlElement(String name, VmMapping attributes, VmDynamic... elements) {
    return buildXmlElement(
        name,
        attributes,
        builder -> {
          for (var element : elements) {
            builder.addElement(element);
          }
        });
  }

  private VmDynamic buildXmlElement(
      String name, VmMapping attributes, Consumer<VmObjectBuilder> gen) {
    var builder =
        new VmObjectBuilder()
            .addProperty(Identifier.IS_XML_ELEMENT, true)
            .addProperty(Identifier.NAME, name)
            .addProperty(Identifier.ATTRIBUTES, attributes)
            .addProperty(Identifier.IS_BLOCK_FORMAT, true);
    gen.accept(builder);
    return builder.toDynamic();
  }

  private VmMapping buildAttributes(@Nullable Object... attributes) {
    var builder = new VmObjectBuilder();
    for (var i = 0; i < attributes.length; i += 2) {
      var key = attributes[i];
      var value = attributes[i + 1];
      if (key == null || value == null) {
        continue;
      }
      builder.addEntry(key, value);
    }
    return builder.toMapping();
  }

  private VmTyped makeCdata(String text) {
    return new VmObjectBuilder(1)
        .addProperty(Identifier.TEXT, text)
        .toTyped(XmlModule.getCDataClass());
  }

  private String stripColors(String str) {
    return str.replaceAll("\033\\[[;\\d]*m", "");
  }

  private static String renderXML(String indent, String version, VmDynamic value) {
    var builder = new StringBuilder();
    var renderer =
        new Renderer(
            builder,
            indent,
            version,
            "",
            VmMapping.empty(),
            PklConverter.NOOP,
            IndirectCallNode.getUncached());
    renderer.renderDocument(value);
    return builder.toString();
  }
}
