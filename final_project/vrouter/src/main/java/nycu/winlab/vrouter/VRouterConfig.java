/*
 * Copyright 2023-present Open Networking Foundation
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
package nycu.winlab.vrouter;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.onosproject.net.ConnectPoint;
import org.onlab.packet.MacAddress;
import org.onlab.packet.IpAddress;
import org.onlab.packet.Ip6Address;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class VRouterConfig extends Config<ApplicationId> {
    public static final String QUAGGA_LOCATION = "vrrouting";
    public static final String QUAGGA_MAC = "vrrouting-mac";
    public static final String VIRTUAL_IP = "gateway-ipv4";
    public static final String VIRTUAL_IP6 = "gateway-ipv6";
    public static final String VIRTUAL_MAC = "gateway-mac";
    public static final String PEERS = "v4-peers";
    public static final String V6_PEERS = "v6-peers";


    Function<String, String> func = (String e) -> {
        return e;
    };

    @Override
    public boolean isValid() {
        return hasFields(QUAGGA_LOCATION, QUAGGA_MAC, VIRTUAL_IP, VIRTUAL_IP6, VIRTUAL_MAC, PEERS, V6_PEERS);
    }

    public ArrayList<ConnectPoint> getVrroutingCP() {
        List<String> peers = getList(QUAGGA_LOCATION, func);
        ArrayList<ConnectPoint> peersCp = new ArrayList<ConnectPoint>();

        for (String peerCp : peers) {
            peersCp.add(ConnectPoint.fromString(peerCp));
        }

        return peersCp;
    }

    public MacAddress getVrroutingMac() {
        return MacAddress.valueOf(get(QUAGGA_MAC, null));
    }

    public IpAddress getVirtualIP() {
        return IpAddress.valueOf(get(VIRTUAL_IP, null));
    }

    public Ip6Address getVirtualIP6() {
        return Ip6Address.valueOf(get(VIRTUAL_IP6, null));
    }

    public MacAddress getVirtualMac() {
        return MacAddress.valueOf(get(VIRTUAL_MAC, null));
    }

    public ArrayList<IpAddress> getPeers() {
        List<String> peers = getList(PEERS, func);
        ArrayList<IpAddress> peersIp = new ArrayList<IpAddress>();

        for (String peerIp : peers) {
            peersIp.add(IpAddress.valueOf(peerIp));
        }

        return peersIp;
    }

    public ArrayList<Ip6Address> getPeers6() {
        List<String> peers = getList(V6_PEERS, func);
        ArrayList<Ip6Address> peersIp = new ArrayList<Ip6Address>();

        for (String peerIp : peers) {
            peersIp.add(Ip6Address.valueOf(peerIp));
        }

        return peersIp;
    }
}