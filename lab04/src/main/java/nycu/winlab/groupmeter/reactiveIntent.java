// /*
//  * Copyright 2024-present Open Networking Foundation
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *     http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */
// package nycu.winlab.groupmeter;

// import java.nio.ByteBuffer;
// import java.util.HashMap;
// import java.util.Map;
// // import java.util.List;
// import java.util.Collections;
// import java.util.Arrays;

// import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
// import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
// import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

// import org.onosproject.core.ApplicationId;
// import org.onosproject.core.CoreService;
// import org.onosproject.net.config.ConfigFactory;
// import org.onosproject.net.config.NetworkConfigEvent;
// import org.onosproject.net.config.NetworkConfigListener;
// import org.onosproject.net.config.NetworkConfigRegistry;
// import org.onosproject.net.packet.PacketProcessor;
// import org.onosproject.net.packet.PacketContext;
// import org.onosproject.net.packet.InboundPacket;
// import org.onosproject.net.packet.DefaultOutboundPacket;
// import org.onosproject.net.packet.OutboundPacket;
// import org.onosproject.net.packet.PacketPriority;
// import org.onosproject.net.packet.PacketService;
// import org.onosproject.net.PortNumber;
// import org.onosproject.net.FilteredConnectPoint;

// import org.onlab.packet.Ethernet;
// import org.onlab.packet.MacAddress;
// import org.onlab.packet.ARP;
// import org.onlab.packet.IpAddress;
// import org.onlab.packet.IPv4;
// import org.onlab.packet.UDP;

// import org.osgi.service.component.annotations.Activate;
// import org.osgi.service.component.annotations.Component;
// import org.osgi.service.component.annotations.Deactivate;
// import org.osgi.service.component.annotations.Reference;
// import org.osgi.service.component.annotations.ReferenceCardinality;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.onosproject.core.GroupId;

// import org.onosproject.net.intent.PointToPointIntent;
// import org.onosproject.net.intent.IntentService;
// import org.onosproject.net.ConnectPoint;
// import org.onosproject.net.DeviceId;
// import org.onosproject.net.flow.TrafficTreatment;
// import org.onosproject.net.flow.DefaultTrafficTreatment;
// import org.onosproject.net.flow.TrafficSelector;
// import org.onosproject.net.flow.DefaultTrafficSelector;
// import org.onosproject.net.flow.FlowRule;
// import org.onosproject.net.flow.DefaultFlowRule;
// import org.onosproject.net.flow.FlowRuleService;

// import org.onosproject.net.meter.Meter;
// import org.onosproject.net.meter.Band;
// import org.onosproject.net.meter.MeterService;
// import org.onosproject.net.meter.MeterRequest;
// import org.onosproject.net.meter.DefaultMeterRequest;
// import org.onosproject.net.meter.DefaultBand;
// import org.onosproject.net.meter.MeterId;

// // import org.onosproject.net.group.Group;
// import org.onosproject.net.group.DefaultGroupDescription;
// import org.onosproject.net.group.DefaultGroupKey;
// import org.onosproject.net.group.DefaultGroupBucket;
// import org.onosproject.net.group.GroupBucket;
// import org.onosproject.net.group.GroupBuckets;
// import org.onosproject.net.group.GroupDescription;
// import org.onosproject.net.group.GroupKey;
// import org.onosproject.net.group.GroupService;

// /** Sample Network Configuration Service Application. **/
// @Component(immediate = true)
// public class AppComponent {

//     private final Logger log = LoggerFactory.getLogger(getClass());
//     private final NameConfigListener cfgListener = new NameConfigListener();


//     private final ConfigFactory<ApplicationId, NameConfig> factory = new ConfigFactory<ApplicationId, NameConfig>(
//         APP_SUBJECT_FACTORY, NameConfig.class, "informations") {
//         @Override
//         public NameConfig createConfig() {
//         return new NameConfig();
//         }
//     };

//     private ApplicationId appId;

//     @Reference(cardinality = ReferenceCardinality.MANDATORY)
//     protected NetworkConfigRegistry cfgService;

//     @Reference(cardinality = ReferenceCardinality.MANDATORY)
//     protected CoreService coreService;

//     @Reference(cardinality = ReferenceCardinality.MANDATORY)
//     protected GroupService groupService;

//     @Reference(cardinality = ReferenceCardinality.MANDATORY)
//     protected MeterService meterService;

//     @Reference(cardinality = ReferenceCardinality.MANDATORY)
//     protected IntentService intentService;

//     @Reference(cardinality = ReferenceCardinality.MANDATORY)
//     protected FlowRuleService flowRuleService;

//     @Reference(cardinality = ReferenceCardinality.MANDATORY)
//     protected PacketService packetService;

//     private UdpPacketProcessor udpProcessor = new UdpPacketProcessor();
//     private ArpPacketProcessor arpProcessor = new ArpPacketProcessor();
//     // Example constants (replace with actual values as needed)
//     private DeviceId s1 = DeviceId.deviceId("of:0000000000000001");
//     private DeviceId s2 = DeviceId.deviceId("of:0000000000000002");
//     private DeviceId s4 = DeviceId.deviceId("of:0000000000000004");
//     private DeviceId s5 = DeviceId.deviceId("of:0000000000000005");

//     private MacAddress h1Mac; // replace with actual h1 MAC
//     private MacAddress h2Mac; // replace with actual h2 MAC
//     private IpAddress h1Ip; // replace with actual h1 IP
//     private IpAddress h2Ip;
//     // ConnectPoints for h1, h2, and relevant switch ports
//     private ConnectPoint h1ConnectPoint;
//     private ConnectPoint h2ConnectPoint;

//     private Map<String, String> informationsMap;
//     private Map<IpAddress, MacAddress> arpTable = new HashMap<>();
//     private static final int GROUP_ID = 1;
//     private MeterId meterId;

//     private ConnectPoint createConnectPoint(String info) {
//         // Split the string by "/" to get device ID and port number
//         String[] parts = info.split("/");
//         if (parts.length != 2) {
//             throw new IllegalArgumentException("Invalid ConnectPoint format: " + info);
//         }
//         String deviceId = parts[0]; // "of:0000000000000001"
//         PortNumber portNumber = PortNumber.portNumber(parts[1]); // Port number (e.g., 1)

//         return new ConnectPoint(DeviceId.deviceId(deviceId), portNumber);
//     }

//     @Activate
//     protected void activate() {
//         appId = coreService.registerApplication("nycu.winlab.groupmeter");
//         cfgService.addListener(cfgListener);
//         cfgService.registerConfigFactory(factory);

//         // Add ARP and UDP processors with different priorities
//         packetService.addProcessor(arpProcessor, PacketProcessor.director(3)); // Higher priority for ARP
//         packetService.addProcessor(udpProcessor, PacketProcessor.director(2)); // Lower priority for UDP

//         // Request ARP packets
//         TrafficSelector.Builder arpSelector = DefaultTrafficSelector.builder();
//         arpSelector.matchEthType(Ethernet.TYPE_ARP);
//         packetService.requestPackets(arpSelector.build(), PacketPriority.REACTIVE, appId);

//         // Request IPv4 packets for UDP processing
//         TrafficSelector.Builder ipv4Selector = DefaultTrafficSelector.builder();
//         ipv4Selector.matchEthType(Ethernet.TYPE_IPV4);
//         packetService.requestPackets(ipv4Selector.build(), PacketPriority.REACTIVE, appId);

//         // Install Group Table and Meter Table
//         setupGroupTableOnS1();
//         installFlowRuleWithGroupIdOnS1();
//         setupMeterOnS4();
//         installFlowRuleOnS4();
//         log.info("Started");
//     }

//     @Deactivate
//     protected void deactivate() {
//         cfgService.removeListener(cfgListener);
//         cfgService.unregisterConfigFactory(factory);
//         packetService.cancelPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP).build(),
//                 PacketPriority.REACTIVE, appId);
//         packetService.removeProcessor(arpProcessor);
//         // remove flowrule installed by your app
//         flowRuleService.removeFlowRulesById(appId);

//         // remove your packet processor
//         packetService.removeProcessor(udpProcessor);
//         // remove flowrule you installed for packet-in
//         TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
//         selector.matchEthType(Ethernet.TYPE_IPV4);
//         packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
//         log.info("Stopped");
//     }

//     private class NameConfigListener implements NetworkConfigListener {
//         @Override
//         public void event(NetworkConfigEvent event) {
//             if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
//                 && event.configClass().equals(NameConfig.class)) {
//                 NameConfig config = cfgService.getConfig(appId, NameConfig.class);
//                 // String informationsMap = config.informations();
//                 informationsMap = config.informations();
//                 if (config != null) {
//                     // config.informations();
//                     log.info("ConnectPoint_h1: {}, ConnectPoint_h2: {}", informationsMap.get("host-1"),
//                         informationsMap.get("host-2"));
//                     log.info("MacAddress_h1: {}, MacAddress _h2: {}", informationsMap.get("mac-1"),
//                         informationsMap.get("mac-2"));
//                     log.info("IpAddress_h1: {}, IpAddress_h2: {}", informationsMap.get("ip-1"),
//                         informationsMap.get("ip-2"));
//                     // create arpTable
//                     h1Ip = IpAddress.valueOf(informationsMap.get("ip-1"));
//                     h2Ip = IpAddress.valueOf(informationsMap.get("ip-2"));
//                     h1Mac = MacAddress.valueOf(informationsMap.get("mac-1"));
//                     h2Mac = MacAddress.valueOf(informationsMap.get("mac-2"));
//                     arpTable.put(h1Ip, h1Mac);
//                     arpTable.put(h2Ip, h2Mac);
//                     // create ConnectionPoint
//                     h1ConnectPoint = createConnectPoint(informationsMap.get("host-1"));
//                     h2ConnectPoint = createConnectPoint(informationsMap.get("host-2"));
//                 }
//             }
//         }
//     }

//     private class ArpPacketProcessor  implements PacketProcessor {
//         @Override
//         public void process(PacketContext context) {
//             if (context.isHandled()) {
//                 return;
//             }
//             InboundPacket pkt = context.inPacket();
//             Ethernet ethPkt = pkt.parsed();
//             ARP arpPkt = (ARP) ethPkt.getPayload();
//             IpAddress srcIp = IpAddress.valueOf(IpAddress.Version.valueOf("INET"),
//                 arpPkt.getSenderProtocolAddress());
//             IpAddress dstIp = IpAddress.valueOf(IpAddress.Version.valueOf("INET"),
//                 arpPkt.getTargetProtocolAddress());
//             short opcode = arpPkt.getOpCode();
//             DeviceId recDevId = pkt.receivedFrom().deviceId();
//             PortNumber recPort = pkt.receivedFrom().port();

//             if (opcode == ARP.OP_REQUEST) {
//                 if (arpTable.get(dstIp) != null) {
//                     MacAddress dstMac = arpTable.get(dstIp);
//                     log.info("TABLE HIT. Requested MAC = " + dstMac);
//                     Ethernet eth = ARP.buildArpReply(dstIp.getIp4Address(),
//                                                     dstMac,
//                                                     ethPkt);
//                     OutboundPacket outboundPacket = new DefaultOutboundPacket(pkt.receivedFrom().deviceId(),
//                         DefaultTrafficTreatment.builder().setOutput(pkt.receivedFrom().port()).build(),
//                         ByteBuffer.wrap(eth.serialize()));
//                     packetService.emit(outboundPacket);
//                     return;
//                 }
//             }
//         }
//     }

//     // UdpPacketProcessor class
//     private class UdpPacketProcessor implements PacketProcessor {

//         @Override
//         public void process(PacketContext context) {
//             // Stop processing if this packet has already been handled (e.g., dropped)
//             if (context.isHandled()) {
//                 return;
//             }

//             // Extract the incoming packet as an Ethernet frame
//             InboundPacket pkt = context.inPacket();
//             Ethernet ethPkt = pkt.parsed();

//             if (ethPkt == null) {
//                 return; // Packet not parsed properly, skip
//             }

//             // Check if the Ethernet frame carries an IPv4 packet
//             if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
//                 IPv4 ipv4Pkt = (IPv4) ethPkt.getPayload();

//                 // Check if the IPv4 packet carries a UDP payload
//                 if (ipv4Pkt.getProtocol() == IPv4.PROTOCOL_UDP) {
//                     UDP udpPkt = (UDP) ipv4Pkt.getPayload();

//                     // Determine the ingress point (where the packet came from)
//                     ConnectPoint ingressPoint = context.inPacket().receivedFrom();

//                     // Check if the destination IP or MAC address matches h1 or h2
//                     IpAddress dstIp = IpAddress.valueOf(ipv4Pkt.getDestinationAddress());
//                     MacAddress dstMac = ethPkt.getDestinationMAC();

//                     // If packet is destined for h1, set up intent to h1
//                     if (dstIp.equals(h1Ip) || dstMac.equals(h1Mac)) {
//                         setupIntent(ingressPoint, h1ConnectPoint, h1Mac);
//                     } else if (dstIp.equals(h2Ip) || dstMac.equals(h2Mac)) {
//                         setupIntent(ingressPoint, h2ConnectPoint, h2Mac);
//                     }
//                 }
//             }
//         }
//     }

//     private void setupGroupTableOnS1() {
//         GroupKey groupKey = new DefaultGroupKey(appId.name().getBytes());
//         log.info("Defining failover group on s1 with buckets to s2 and s4.");
//         // Define the traffic treatments for each bucket
//         TrafficTreatment treatmentToS2 = DefaultTrafficTreatment.builder()
//                 .setOutput(PortNumber.portNumber(2)) // s1 to s2 port
//                 .build();

//         TrafficTreatment treatmentToS4 = DefaultTrafficTreatment.builder()
//                 .setOutput(PortNumber.portNumber(3)) // s1 to s4 port
//                 .build();

//         // Define each bucket with a WatchPort to monitor for failures
//         GroupBucket bucketToS2 = DefaultGroupBucket.createFailoverGroupBucket(
//                 treatmentToS2,
//                 PortNumber.portNumber(2),  // WatchPort for bucket 1
//                 null
//         );

//         GroupBucket bucketToS4 = DefaultGroupBucket.createFailoverGroupBucket(
//                 treatmentToS4,
//                 PortNumber.portNumber(3),  // WatchPort for bucket 2
//                 null
//         );

//         // Define group with both buckets (primary and backup)
//         GroupDescription groupDescription = new DefaultGroupDescription(
//                 s1,
//                 GroupDescription.Type.FAILOVER,
//                 new GroupBuckets(Arrays.asList(bucketToS2, bucketToS4)),
//                 // new GroupBuckets(List.of(bucketToS2, bucketToS4)),
//                 groupKey,
//                 GROUP_ID,
//                 appId
//         );

//         // Submit group to group service
//         groupService.addGroup(groupDescription);
//         log.info("Group table on s1 configured with paths to s2 and s4");
//         log.info("Submitting group description to group service for s1 with ID: {}"
// , groupDescription.givenGroupId());
//     }

//     private void installFlowRuleWithGroupIdOnS1() {
//         // Retrieve the group ID from the group service
//         // Group group = groupService.getGroup(s1, new DefaultGroupKey(appId.name().getBytes()));
//         // if (group == null) {
//         //     log.error("Group on s1 is not found.");
//         //     return;
//         // }
//         // GroupId groupId = group.id();

//         // Define the traffic selector for the flow rule
//         TrafficSelector selector = DefaultTrafficSelector.builder()
//                 .matchInPort(PortNumber.portNumber(1))     // Match input port 1
//                 .matchEthType(Ethernet.TYPE_IPV4)              // Match IPv4 protocol 0x0080
//                 .build();

//         // Define the traffic treatment to use the group ID
//         TrafficTreatment treatment = DefaultTrafficTreatment.builder()
//                 .group(GroupId.valueOf(GROUP_ID))           // Forward to the FAILOVER group
//                 .build();

//         // Create and install the flow rule
//         FlowRule flowRule = DefaultFlowRule.builder()
//                 .forDevice(s1)
//                 .withSelector(selector)
//                 .withTreatment(treatment)
//                 .withPriority(500)                        // Adjust priority as needed
//                 .fromApp(appId)
//                 .makePermanent()
//                 .build();

//         flowRuleService.applyFlowRules(flowRule);
//         log.info("Flow rule submitted to FlowRuleService for s1 to use GroupId: {}", GROUP_ID);
//     }

//     private void setupMeterOnS4() {
//         log.info("Creating meter request for device s4 with rate 512 KBps and burst size 1024 KB.");
//         MeterRequest meterRequest = DefaultMeterRequest.builder()
//                 .forDevice(s4)
//                 .fromApp(appId)
//                 .withUnit(Meter.Unit.KB_PER_SEC)
//                 .withBands(Collections.singletonList(
//                         DefaultBand.builder()
//                                 .ofType(Band.Type.DROP)
//                                 .withRate(512)          // Rate in KB_PER_SEC
//                                 .burstSize(1024)        // Burst size in KB
//                                 .build()
//                 ))
//                 .burst()   // Enable burst mode, no .build() is needed
//                 .add();

//         // Submit the meter request to install it on s4
//         Meter meter = meterService.submit(meterRequest);

//         // Check if the meter was successfully created
//         if (meter == null || meter.id() == null) {
//             log.error("Failed to create meter on s4.");
//             return;
//         }

//         // MeterId meterId = meter.id();
//         meterId = meter.id();
//     }

//     private void installFlowRuleOnS4() {
//         // Step 2: Define the traffic selector for the flow rule, matching source MAC from h1
//         TrafficSelector selector = DefaultTrafficSelector.builder()
//                 .matchEthSrc(MacAddress.valueOf(informationsMap.get("mac-1")))  // Match source MAC of h1
//                 .build();

//         // Step 3: Define the traffic treatment using the meter ID and setting output port to 2
//         TrafficTreatment treatment = DefaultTrafficTreatment.builder()
//                 .meter(meterId)                                // Apply the meter
//                 .setOutput(PortNumber.portNumber(2))           // Forward to output port 2
//                 .build();

//         // Step 4: Create and install the flow rule
//         FlowRule flowRule = DefaultFlowRule.builder()
//                 .forDevice(s4)
//                 .withSelector(selector)
//                 .withTreatment(treatment)
//                 .withPriority(30001)                             // Adjust priority as needed
//                 .fromApp(appId)
//                 .makePermanent()
//                 .build();

//         flowRuleService.applyFlowRules(flowRule);
//         log.info("Flow rule on s4 installed with METER_ID for traffic from h1's MAC to output port 2");
//     }

//     private void setupIntent(ConnectPoint ingressPoint, ConnectPoint egressPoint, MacAddress macAddress) {
//         // Create TrafficSelector for the MAC address
//         TrafficSelector selector = DefaultTrafficSelector.builder()
//                 .matchEthDst(macAddress)
//                 .build();

//         // Create TrafficTreatment (if needed, here we simply forward)
//         TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();

//         // Build PointToPointIntent
//         PointToPointIntent intent = PointToPointIntent.builder()
//                 .appId(appId)
//                 .selector(selector)
//                 .treatment(treatment)
//                 .filteredIngressPoint(new FilteredConnectPoint(ingressPoint))
//                 .filteredEgressPoint(new FilteredConnectPoint(egressPoint))
//                 .priority(40000)  // Example priority, adjust as needed
//                 .build();

//         // Submit the intent to the intent service
//         intentService.submit(intent);
//         log.info("Submitted intent from {} to {} for MAC {}", ingressPoint, egressPoint, macAddress);
//     }
// }
