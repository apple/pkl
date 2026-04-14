/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.config.java;

import java.util.Base64;

public class ConfigPklBinaryDecoderTest extends AbstractConfigTest {
  // generate via: pbpaste | ./pkl-cli/build/executable/jpkl eval /dev/stdin -f pkl-binary | base64

  @Override
  protected Config getPigeonConfig() {
    // pigeon { age = 30; friends = List("john", "mary"); address { street = "Fuzzy St." } }
    return Config.fromPklBinary(
        Base64.getDecoder()
            .decode(
                "lAGkdGVzdNklZmlsZTovLy9Vc2Vycy9qYmFzY2gvc3JjL3BrbC90ZXN0LnBrbJGTEKZwaWdlb26UAadEeW5hbWljqHBrbDpiYXNlk5MQo2FnZR6TEKdmcmllbmRzkgSSpGpvaG6kbWFyeZMQp2FkZHJlc3OUAadEeW5hbWljqHBrbDpiYXNlkZMQpnN0cmVldKlGdXp6eSBTdC4="));
  }

  @Override
  protected Config getPigeonModuleConfig() {
    // age = 30; friends = List("john", "mary"); address { street = "Fuzzy St." }
    return Config.fromPklBinary(
        Base64.getDecoder()
            .decode(
                "lAGlc3RkaW6xZmlsZTovLy9kZXYvc3RkaW6TkxCjYWdlHpMQp2ZyaWVuZHOSBJKkam9obqRtYXJ5kxCnYWRkcmVzc5QBp0R5bmFtaWOocGtsOmJhc2WRkxCmc3RyZWV0qUZ1enp5IFN0Lg=="));
  }

  @Override
  protected Config getPairConfig() {
    // x { first = "file/path"; second = 42 }
    return Config.fromPklBinary(
        Base64.getDecoder()
            .decode(
                "lAGlc3RkaW6xZmlsZTovLy9kZXYvc3RkaW6RkxCheJQBp0R5bmFtaWOocGtsOmJhc2WSkxClZmlyc3SpZmlsZS9wYXRokxCmc2Vjb25kKg=="));
  }

  @Override
  protected Config getMapConfig() {
    // x = Map("one", 1, "two", 2)
    return Config.fromPklBinary(
        Base64.getDecoder().decode("lAGlc3RkaW6xZmlsZTovLy9kZXYvc3RkaW6RkxCheJICgqNvbmUBo3R3bwI="));
  }
}
