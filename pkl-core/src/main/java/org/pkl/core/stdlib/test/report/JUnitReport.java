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
package org.pkl.core.stdlib.test.report;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.function.Consumer;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.BaseModule;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.TestResults;
import org.pkl.core.runtime.TestResults.TestSectionResults;
import org.pkl.core.runtime.TestResults.TestSectionResults.Error;
import org.pkl.core.runtime.TestResults.TestSectionResults.TestResult;
import org.pkl.core.runtime.VmDynamic;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.runtime.XmlModule;
import org.pkl.core.stdlib.PklConverter;
import org.pkl.core.stdlib.xml.RendererNodes.Renderer;
import org.pkl.core.util.EconomicMaps;

public final class JUnitReport implements TestReport {

  @Override
  public void report(TestResults results, Writer writer) throws IOException {
    writer.append(renderXML("    ", "1.0", buildSuite(results)));
  }

  private VmDynamic buildSuite(TestResults results) {
    var testCases = testCases(results.moduleName, results.facts);
    testCases.addAll(testCases(results.moduleName, results.examples));

    if (!results.getErr().isBlank()) {
      var err =
          buildXmlElement(
              "system-err",
              VmMapping.empty(),
              members -> members.put("body", syntheticElement(makeCdata(results.getErr()))));
      testCases.add(err);
    }

    var attrs =
        buildAttributes(
            "name", results.moduleName,
            "tests", (long) results.totalTests(),
            "failures", (long) results.totalFailures());

    return buildXmlElement("testsuite", attrs, testCases.toArray(new VmDynamic[0]));
  }

  private ArrayList<VmDynamic> testCases(String moduleName, TestSectionResults testSectionResults) {
    var elements = new ArrayList<VmDynamic>(testSectionResults.totalTests());

    if (testSectionResults.hasError()) {
      var error = error(testSectionResults.getError());

      var attrs =
          buildAttributes("classname", moduleName + "." + testSectionResults.name, "name", "error");
      var element = buildXmlElement("testcase", attrs, error.toArray(new VmDynamic[0]));

      elements.add(element);
    }

    for (var res : testSectionResults.getResults()) {
      var attrs =
          buildAttributes(
              "classname", moduleName + "." + testSectionResults.name, "name", res.name);
      var failures = failures(res);
      var element = buildXmlElement("testcase", attrs, failures.toArray(new VmDynamic[0]));
      elements.add(element);
    }
    return elements;
  }

  private ArrayList<VmDynamic> failures(TestResult res) {
    var list = new ArrayList<VmDynamic>();
    long i = 0;
    for (var fail : res.getFailures()) {
      var attrs = buildAttributes("message", fail.getKind());
      long element = i++;
      list.add(
          buildXmlElement(
              "failure",
              attrs,
              members -> members.put(element, syntheticElement(fail.getRendered()))));
    }
    return list;
  }

  private ArrayList<VmDynamic> error(Error error) {
    var list = new ArrayList<VmDynamic>();
    var attrs = buildAttributes("message", error.getMessage());
    list.add(
        buildXmlElement(
            "error",
            attrs,
            members -> members.put(1, syntheticElement("\n" + error.getRendered()))));
    return list;
  }

  private VmDynamic buildXmlElement(String name, VmMapping attributes, VmDynamic... elements) {
    return buildXmlElement(
        name,
        attributes,
        members -> {
          long i = 0;
          for (var element : elements) {
            members.put(i++, syntheticElement(element));
          }
        });
  }

  private VmDynamic buildXmlElement(
      String name, VmMapping attributes, Consumer<EconomicMap<Object, ObjectMember>> gen) {
    EconomicMap<Object, ObjectMember> members =
        EconomicMaps.of(
            Identifier.IS_XML_ELEMENT,
                VmUtils.createSyntheticObjectProperty(Identifier.IS_XML_ELEMENT, "", true),
            Identifier.NAME, VmUtils.createSyntheticObjectProperty(Identifier.NAME, "", name),
            Identifier.ATTRIBUTES,
                VmUtils.createSyntheticObjectProperty(Identifier.ATTRIBUTES, "", attributes),
            Identifier.IS_BLOCK_FORMAT,
                VmUtils.createSyntheticObjectProperty(Identifier.IS_BLOCK_FORMAT, "", true));
    gen.accept(members);
    return new VmDynamic(
        VmUtils.createEmptyMaterializedFrame(),
        BaseModule.getDynamicClass().getPrototype(),
        members,
        members.size() - 4);
  }

  private VmMapping buildAttributes(Object... attributes) {
    EconomicMap<Object, ObjectMember> attrs = EconomicMaps.create(attributes.length);
    for (int i = 0; i < attributes.length; i += 2) {
      attrs.put(
          attributes[i],
          VmUtils.createSyntheticObjectEntry(attributes[i].toString(), attributes[i + 1]));
    }
    return new VmMapping(
        VmUtils.createEmptyMaterializedFrame(), BaseModule.getMappingClass().getPrototype(), attrs);
  }

  private ObjectMember syntheticElement(Object constantValue) {
    return VmUtils.createSyntheticObjectElement("", constantValue);
  }

  private VmTyped makeCdata(String text) {
    var clazz = XmlModule.getCDataClass();
    // HACK: The property identifier here has to be `null` instead of `Identifier.TEXT` or
    // a `Invalid sharing of AST nodes detected` error will be thrown.
    EconomicMap<Object, ObjectMember> attrs =
        EconomicMaps.of(Identifier.TEXT, VmUtils.createSyntheticObjectProperty(null, "", text));
    return new VmTyped(VmUtils.createEmptyMaterializedFrame(), clazz.getPrototype(), clazz, attrs);
  }

  public static String renderXML(String indent, String version, VmDynamic value) {
    var builder = new StringBuilder();
    var converter = new PklConverter(VmMapping.empty());
    var renderer = new Renderer(builder, indent, version, "", VmMapping.empty(), converter);
    renderer.renderDocument(value);
    return builder.toString();
  }
}
