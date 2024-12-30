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
package nycu.winlab.vrouter;

import java.util.List;

import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.Config;

public class VirtualRouterConfig extends Config<ApplicationId> {

  public static final String VRROUTING_CONNECTED_POINT = "vrrouting";

  public static final String VRROUTING_MAC_ADDRESS = "vrrouting-mac";

  public static final String VIRTUAL_ROUTER_IP_ADDRESS = "gateway-ipv4";

  public static final String VIRTUAL_ROUTER_MAC_ADDRESS = "gateway-mac";

  public static final String EXTERNAL_ROUTER_IP_ADDRESS_LIST = "v4-peers";

  @Override
  public boolean isValid() {
    return hasOnlyFields(VRROUTING_CONNECTED_POINT, VRROUTING_MAC_ADDRESS,
    VIRTUAL_ROUTER_IP_ADDRESS, VIRTUAL_ROUTER_MAC_ADDRESS, EXTERNAL_ROUTER_IP_ADDRESS_LIST);
  }

  public ConnectPoint getVrroutingConnectedPoint() {
    String vrroutingConnectedPointString = get(VRROUTING_CONNECTED_POINT, null);
    String[] vrroutingConnectedPointStringArray = vrroutingConnectedPointString.split("/");
    ConnectPoint vrroutingConnectedPoint = new ConnectPoint(DeviceId.deviceId(
      vrroutingConnectedPointStringArray[0]), PortNumber.portNumber(vrroutingConnectedPointStringArray[1]));
    return vrroutingConnectedPoint;
  }

  public MacAddress getVrroutingMacAddress() {
    String vrroutingMacAddressString = get(VRROUTING_MAC_ADDRESS, null);
    MacAddress vrroutingMacAddress = MacAddress.valueOf(vrroutingMacAddressString);
    return vrroutingMacAddress;
  }

  public IpAddress getVirtualRouterIpAddress() {
    String virtualRouterIpAddressString = get(VIRTUAL_ROUTER_IP_ADDRESS, null);
    IpAddress virtualRouterIpAddress = IpAddress.valueOf(virtualRouterIpAddressString);
    return virtualRouterIpAddress;
  }

  public MacAddress getVirtualRouterMacAddress() {
    String virtualRouterMacAddressString = get(VIRTUAL_ROUTER_MAC_ADDRESS, null);
    MacAddress virtualRouterMacAddress = MacAddress.valueOf(virtualRouterMacAddressString);
    return virtualRouterMacAddress;
  }

  public List<IpAddress> getExternalRouterIpAddressList() {
    return getList(EXTERNAL_ROUTER_IP_ADDRESS_LIST, IpAddress::valueOf, null);
  }
}
