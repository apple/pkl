/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.config.java.mapper;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.pkl.core.Duration;
import org.pkl.core.DurationUnit;

public class ConversionsTest {
  @Test
  public void pStringToFile() {
    var file = Conversions.pStringToFile.converter.convert("relative/path", null);
    assertThat(file).isEqualTo(new File("relative/path"));

    var file2 = Conversions.pStringToFile.converter.convert("/absolute/path", null);
    assertThat(file2).isEqualTo(new File("/absolute/path"));

    var file3 = Conversions.pStringToFile.converter.convert("", null);
    assertThat(file3).isEqualTo(new File(""));
  }

  @Test
  public void pStringToPath() {
    var path = Conversions.pStringToPath.converter.convert("relative/path", null);
    assertThat(path).isEqualTo(Path.of("relative/path"));

    var path2 = Conversions.pStringToPath.converter.convert("/absolute/path", null);
    assertThat(path2).isEqualTo(Path.of("/absolute/path"));

    var path3 = Conversions.pStringToPath.converter.convert("", null);
    assertThat(path3).isEqualTo(Path.of(""));
  }

  @Test
  public void pStringToPattern() {
    var str = "(?i)\\w*";
    var pattern = Conversions.pStringToPattern.converter.convert(str, null);
    assertThat(pattern.pattern()).isEqualTo(str);
  }

  @Test
  public void pRegexToString() {
    var regex = Pattern.compile("(?i)\\w*");
    var str = Conversions.pRegexToString.converter.convert(regex, null);
    assertThat(str).isEqualTo("(?i)\\w*");
  }

  @Test
  public void pDurationToDuration() {
    var pDuration = new Duration(100, DurationUnit.MINUTES);
    var duration = Conversions.pDurationToDuration.converter.convert(pDuration, null);
    assertThat(duration).isEqualTo(java.time.Duration.of(100, ChronoUnit.MINUTES));
  }
}
