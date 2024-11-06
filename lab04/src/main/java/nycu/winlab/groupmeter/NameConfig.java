/*
 * Copyright 2020-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nycu.winlab.groupmeter;
import java.util.Map;
import java.util.HashMap;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
public class NameConfig extends Config<ApplicationId> {

    public static final String HOST1 = "host-1";
    public static final String HOST2 = "host-2";
    public static final String MAC1 = "mac-1";
    public static final String MAC2 = "mac-2";
    public static final String IP1 = "ip-1";
    public static final String IP2 = "ip-2";

    @Override
    public boolean isValid() {
        // ObjectNode informationsNode = (ObjectNode) get(INFORMATIONS, null);
        return hasFields(HOST1, HOST2, MAC1, MAC2, IP1, IP2);
    }

    public Map<String, String> informations() {
        Map<String, String> informationsMap = new HashMap<>();
        informationsMap.put("host-1", get("host-1", null));
        informationsMap.put("host-2", get("host-2", null));
        informationsMap.put("mac-1", get("mac-1", null));
        informationsMap.put("mac-2", get("mac-2", null));
        informationsMap.put("ip-1", get("ip-1", null));
        informationsMap.put("ip-2", get("ip-2", null));
        return informationsMap;
    }
}