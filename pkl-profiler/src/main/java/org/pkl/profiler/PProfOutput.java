/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.profiler;

import com.google.perftools.profiles.ProfileProto;
import com.google.perftools.profiles.ProfileProto.Label;
import com.google.perftools.profiles.ProfileProto.Profile;
import com.google.perftools.profiles.ProfileProto.ValueType;
import com.oracle.truffle.tools.profiler.CPUSampler.Payload;
import com.oracle.truffle.tools.profiler.ProfilerNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public final class PProfOutput extends ProfilerOutput {
  private final List<String> stringTable = new ArrayList<>();
  private final Map<String, Integer> stringIndices = new HashMap<>();
  private final Map<String, Long> functionIdsByLabel = new HashMap<>();
  private final Map<String, Long> locationIdsByLabel = new HashMap<>();
  private final Profile.Builder profileBuilder = Profile.newBuilder();
  private long mappingId = 0; // 0 = not yet built; ids are nonzero per the proto's contract

  public PProfOutput(PklProfilerData data, Path outputFile) {
    super(data, outputFile);
  }

  @Override
  protected void doWriteOutput(PklProfilerData data, Path outputFile) throws Exception {
    var profile = buildProfile(data);
    Files.createDirectories(outputFile.getParent());
    writeOutputFile(profile, outputFile);
  }

  private Profile buildProfile(PklProfilerData profilerData) {
    internString(""); // string_table[0] must be ""

    profileBuilder.addSampleType(
        ValueType.newBuilder()
            .setType(internString("samples"))
            .setUnit(internString("count"))
            .build());
    profileBuilder.setPeriodType(
        ValueType.newBuilder()
            .setType(internString("wall"))
            .setUnit(internString("milliseconds"))
            .build());
    profileBuilder.setPeriod(profilerData.samplePeriodMs());
    profileBuilder.setTimeNanos(profilerData.startTime().getNano());

    for (var data : profilerData.dataList()) {
      for (var entry : data.getThreadData().entrySet()) {
        // in practice there should only ever be one thread; Pkl is single-threaded.
        var threadLabel =
            Label.newBuilder()
                .setKey(internString("thread"))
                .setStr(internString(entry.getKey().getName()))
                .build();
        // entry.getValue() holds the roots of the call tree for this thread; walk down into
        // children to build one sample per stack (not per root).
        for (var root : entry.getValue()) {
          addSamples(root, new ArrayDeque<>(), threadLabel);
        }
      }
    }
    profileBuilder.addAllStringTable(stringTable);
    return profileBuilder.build();
  }

  // `locationIds` is leaf-first (current node is always at the head), matching the order
  // required by `Sample.location_id`.
  private void addSamples(ProfilerNode<Payload> node, Deque<Long> locationIds, Label threadLabel) {
    locationIds.push(buildLocation(node, profileBuilder));
    var payload = node.getPayload();
    // Split self hits by tier so a stalled-in-the-interpreter hot path is distinguishable from
    // one that got compiled.
    // tier 0: interpreter
    // tier 1+: compiled
    for (var tier = 0; tier < payload.getNumberOfTiers(); tier++) {
      var selfHitCount = payload.getTierSelfCount(tier);
      if (selfHitCount > 0) {
        var sample =
            ProfileProto.Sample.newBuilder().addLabel(threadLabel).addLabel(buildTierLabel(tier));
        for (var locationId : locationIds) {
          sample.addLocationId(locationId);
        }
        sample.addValue(selfHitCount);
        profileBuilder.addSample(sample);
      }
    }
    for (var child : node.getChildren()) {
      addSamples(child, locationIds, threadLabel);
    }
    locationIds.pop();
  }

  private Label buildTierLabel(int tier) {
    return Label.newBuilder()
        .setKey(internString("tier"))
        .setStr(internString(tierLabel(tier)))
        .build();
  }

  private String tierLabel(int tier) {
    if (tier == 0) {
      return "interpreted";
    }
    return "compiled-" + tier;
  }

  private void writeOutputFile(Profile profile, Path outputFile) throws IOException {
    try (var outputStream = new GZIPOutputStream(Files.newOutputStream(outputFile))) {
      profile.writeTo(outputStream);
    }
  }

  private long buildLocation(ProfilerNode<Payload> frame, Profile.Builder profileBuilder) {
    var frameRootName = frame.getRootName();
    var isInterpreted = frame.getPayload().getTierSelfCount(0) > 0;
    var suffix = isInterpreted ? " (interpreted)" : " (jit)";
    var name = frameRootName + suffix;
    return locationIdsByLabel.computeIfAbsent(
        name,
        rootName -> {
          var functionId = buildFunctionId(frame, rootName);
          var id = locationIdsByLabel.size() + 1L;
          var line = ProfileProto.Line.newBuilder().setFunctionId(functionId);
          var sourceSection = frame.getSourceSection();
          if (sourceSection.isAvailable()) {
            line.setLine(sourceSection.getStartLine());
          }
          profileBuilder.addLocation(
              ProfileProto.Location.newBuilder()
                  .setId(id)
                  .setMappingId(buildMapping())
                  .addLine(line.build())
                  .build());
          return id;
        });
  }

  private long buildMapping() {
    if (mappingId != 0) {
      return mappingId;
    }
    mappingId = 1;
    profileBuilder.addMapping(
        ProfileProto.Mapping.newBuilder()
            .setId(mappingId)
            .setFilename(internString("pkl"))
            .setHasFunctions(true)
            .setHasFilenames(true)
            .setHasLineNumbers(true)
            .build());
    return mappingId;
  }

  private long buildFunctionId(ProfilerNode<Payload> frame, String rootName) {
    return functionIdsByLabel.computeIfAbsent(
        rootName,
        name -> {
          var id = functionIdsByLabel.size() + 1L;
          var nameId = internString(name);
          var function =
              ProfileProto.Function.newBuilder().setId(id).setName(nameId).setSystemName(nameId);
          var sourceSection = frame.getSourceSection();
          if (sourceSection.isAvailable()) {
            var sourceUri = sourceSection.getSource().getURI();
            if (sourceUri != null
                && sourceUri.getScheme() != null
                && sourceUri.getScheme().equalsIgnoreCase("file")) {
              function.setFilename(internString(sourceUri.getPath()));
            }
            function.setStartLine(sourceSection.getStartLine());
          }
          profileBuilder.addFunction(function);
          return id;
        });
  }

  private long internString(String str) {
    var existing = stringIndices.get(str);
    if (existing != null) {
      return existing;
    }
    var index = stringTable.size();
    stringTable.add(str);
    stringIndices.put(str, index);
    return index;
  }
}
