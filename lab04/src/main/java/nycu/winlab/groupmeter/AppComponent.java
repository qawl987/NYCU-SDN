/*
 * Copyright 2024-present Open Networking Foundation
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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Arrays;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.PortNumber;
import org.onosproject.net.FilteredConnectPoint;

import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.packet.ARP;
import org.onlab.packet.IpAddress;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onosproject.core.GroupId;

import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.FlowRuleService;

import org.onosproject.net.meter.Meter;
import org.onosproject.net.meter.Band;
import org.onosproject.net.meter.MeterService;
import org.onosproject.net.meter.MeterRequest;
import org.onosproject.net.meter.DefaultMeterRequest;
import org.onosproject.net.meter.DefaultBand;
import org.onosproject.net.meter.MeterId;

import org.onosproject.net.group.DefaultGroupDescription;
import org.onosproject.net.group.DefaultGroupKey;
import org.onosproject.net.group.DefaultGroupBucket;
import org.onosproject.net.group.GroupBucket;
import org.onosproject.net.group.GroupBuckets;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.group.GroupKey;
import org.onosproject.net.group.GroupService;

/** Sample Network Configuration Service Application. **/
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final NameConfigListener cfgListener = new NameConfigListener();


    private final ConfigFactory<ApplicationId, NameConfig> factory = new ConfigFactory<ApplicationId, NameConfig>(
        APP_SUBJECT_FACTORY, NameConfig.class, "informations") {
        @Override
        public NameConfig createConfig() {
        return new NameConfig();
        }
    };

    private ApplicationId appId;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected GroupService groupService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MeterService meterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    private SelfPacketProcessor processor = new SelfPacketProcessor();
    private DeviceId s1 = DeviceId.deviceId("of:0000000000000001");
    private DeviceId s2 = DeviceId.deviceId("of:0000000000000002");
    private DeviceId s4 = DeviceId.deviceId("of:0000000000000004");
    private DeviceId s5 = DeviceId.deviceId("of:0000000000000005");

    private MacAddress h1Mac;
    private MacAddress h2Mac;
    private IpAddress h1Ip;
    private IpAddress h2Ip;
    private ConnectPoint h1ConnectPoint;
    private ConnectPoint h2ConnectPoint;

    private Map<String, String> informationsMap;
    private Map<IpAddress, MacAddress> arpTable = new HashMap<>();
    private static final int GROUP_ID = 1;
    private MeterId meterId;

    private ConnectPoint createConnectPoint(String info) {
        // Split the string by "/" to get device ID and port number
        String[] parts = info.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid ConnectPoint format: " + info);
        }
        String deviceId = parts[0]; // "of:0000000000000001"
        PortNumber portNumber = PortNumber.portNumber(parts[1]); // Port number

        return new ConnectPoint(DeviceId.deviceId(deviceId), portNumber);
    }

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nycu.winlab.groupmeter");
        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);

        packetService.addProcessor(processor, PacketProcessor.director(2));

        // Request ARP packets
        TrafficSelector.Builder arpSelector = DefaultTrafficSelector.builder();
        arpSelector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(arpSelector.build(), PacketPriority.REACTIVE, appId);

        // Request IPv4 packets for UDP processing
        TrafficSelector.Builder ipv4Selector = DefaultTrafficSelector.builder();
        ipv4Selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(ipv4Selector.build(), PacketPriority.REACTIVE, appId);

        // Install Group Table and Meter Table
        setupGroupTableOnS1();
        installFlowRuleWithGroupIdOnS1();
        setupMeterOnS4();
        installFlowRuleOnS4();
        setupIntents();
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(cfgListener);
        cfgService.unregisterConfigFactory(factory);
        packetService.cancelPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP).build(),
                PacketPriority.REACTIVE, appId);
        // remove flowrule you installed for packet-in
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);

        packetService.removeProcessor(processor);
        // remove flowrule installed by your app
        flowRuleService.removeFlowRulesById(appId);
        log.info("Stopped");
    }

    private class NameConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
                && event.configClass().equals(NameConfig.class)) {
                NameConfig config = cfgService.getConfig(appId, NameConfig.class);
                informationsMap = config.informations();
                if (config != null) {
                    log.info(
                        "ConnectPoint_h1: {}, ConnectPoint_h2: {}\n" +
                        "MacAddress_h1: {}, MacAddress_h2: {}\n" +
                        "IpAddress_h1: {}, IpAddress_h2: {}",
                        informationsMap.get("host-1"), informationsMap.get("host-2"),
                        informationsMap.get("mac-1"), informationsMap.get("mac-2"),
                        informationsMap.get("ip-1"), informationsMap.get("ip-2")
                    );
                    // create arpTable
                    h1Ip = IpAddress.valueOf(informationsMap.get("ip-1"));
                    h2Ip = IpAddress.valueOf(informationsMap.get("ip-2"));
                    h1Mac = MacAddress.valueOf(informationsMap.get("mac-1"));
                    h2Mac = MacAddress.valueOf(informationsMap.get("mac-2"));
                    arpTable.put(h1Ip, h1Mac);
                    arpTable.put(h2Ip, h2Mac);
                    // create ConnectionPoint
                    h1ConnectPoint = createConnectPoint(informationsMap.get("host-1"));
                    h2ConnectPoint = createConnectPoint(informationsMap.get("host-2"));
                }
            }
        }
    }

    private class SelfPacketProcessor  implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            if (context.isHandled()) {
                return;
            }
            Ethernet ethPacket = context.inPacket().parsed();
            if (ethPacket == null) {
                return;
            }

            // Check the EtherType to distinguish between ARP and IPv4 packets
            if (ethPacket.getEtherType() == Ethernet.TYPE_ARP) {
                handleArpPacket(context);
            } else if (ethPacket.getEtherType() == Ethernet.TYPE_IPV4) {
                handleIpv4Packet(context);
            }
        }
    }

    private void handleArpPacket(PacketContext context) {
        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();
        ARP arpPkt = (ARP) ethPkt.getPayload();
        IpAddress srcIp = IpAddress.valueOf(IpAddress.Version.valueOf("INET"),
            arpPkt.getSenderProtocolAddress());
        IpAddress dstIp = IpAddress.valueOf(IpAddress.Version.valueOf("INET"),
            arpPkt.getTargetProtocolAddress());
        short opcode = arpPkt.getOpCode();
        DeviceId recDevId = pkt.receivedFrom().deviceId();
        PortNumber recPort = pkt.receivedFrom().port();

        if (opcode == ARP.OP_REQUEST) {
            if (arpTable.get(dstIp) != null) {
                MacAddress dstMac = arpTable.get(dstIp);
                log.info("TABLE HIT. Requested MAC = " + dstMac);
                Ethernet eth = ARP.buildArpReply(dstIp.getIp4Address(),
                                                dstMac,
                                                ethPkt);
                OutboundPacket outboundPacket = new DefaultOutboundPacket(pkt.receivedFrom().deviceId(),
                    DefaultTrafficTreatment.builder().setOutput(pkt.receivedFrom().port()).build(),
                    ByteBuffer.wrap(eth.serialize()));
                packetService.emit(outboundPacket);
                return;
            }
        }
    }

    private void handleIpv4Packet(PacketContext context) {
        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();

        if (ethPkt == null) {
            return;
        }
    }

    private void setupGroupTableOnS1() {
        GroupKey groupKey = new DefaultGroupKey(appId.name().getBytes());
        log.info("Defining failover group on s1 with buckets to s2 and s4.");
        TrafficTreatment treatmentToS2 = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.portNumber(2)) // s1 to s2 port
                .build();

        TrafficTreatment treatmentToS4 = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.portNumber(3)) // s1 to s4 port
                .build();

        GroupBucket bucketToS2 = DefaultGroupBucket.createFailoverGroupBucket(
                treatmentToS2,
                PortNumber.portNumber(2),  // WatchPort for bucket 1
                null
        );
        GroupBucket bucketToS4 = DefaultGroupBucket.createFailoverGroupBucket(
                treatmentToS4,
                PortNumber.portNumber(3),  // WatchPort for bucket 2
                null
        );

        GroupDescription groupDescription = new DefaultGroupDescription(
                s1,
                GroupDescription.Type.FAILOVER,
                new GroupBuckets(Arrays.asList(bucketToS2, bucketToS4)),
                groupKey,
                GROUP_ID,
                appId
        );

        groupService.addGroup(groupDescription);
        log.info("Group table on s1 configured with paths to s2 and s4");
        log.info("Submitting group description to group service for s1 with ID: {}", groupDescription.givenGroupId());
    }

    private void installFlowRuleWithGroupIdOnS1() {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(PortNumber.portNumber(1))
                .matchEthType(Ethernet.TYPE_IPV4)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .group(GroupId.valueOf(GROUP_ID))
                .build();

        // Create and install the flow rule
        FlowRule flowRule = DefaultFlowRule.builder()
                .forDevice(s1)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(500)
                .fromApp(appId)
                .makePermanent()
                .build();

        flowRuleService.applyFlowRules(flowRule);
        log.info("Flow rule submitted to FlowRuleService for s1 to use GroupId: {}", GROUP_ID);
    }

    private void setupMeterOnS4() {
        log.info("Creating meter request for device s4");
        MeterRequest meterRequest = DefaultMeterRequest.builder()
                .forDevice(s4)
                .fromApp(appId)
                .withUnit(Meter.Unit.KB_PER_SEC)
                .withBands(Collections.singletonList(
                        DefaultBand.builder()
                                .ofType(Band.Type.DROP)
                                .withRate(512)          // Rate in KB_PER_SEC
                                .burstSize(1024)        // Burst size in KB
                                .build()
                ))
                .burst()
                .add(); // Here different no build(), instead add() and remove()

        Meter meter = meterService.submit(meterRequest);

        if (meter == null || meter.id() == null) {
            log.error("Failed to create meter on s4.");
            return;
        }
        meterId = meter.id();
        log.info("Meter id build is: {}", meterId);
    }

    private void installFlowRuleOnS4() {
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthSrc(MacAddress.valueOf(informationsMap.get("mac-1")))  // Match source MAC of h1
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .meter(meterId)
                .setOutput(PortNumber.portNumber(2))
                .build();

        // Step 4: Create and install the flow rule
        FlowRule flowRule = DefaultFlowRule.builder()
                .forDevice(s4)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(30001)
                .fromApp(appId)
                .makePermanent()
                .build();

        flowRuleService.applyFlowRules(flowRule);
        log.info("Flow rule on s4 installed with METER_ID for traffic from h1's MAC to output port 2");
    }

    private void setupIntents() {
        // s1 to h2
        TrafficSelector.Builder selector1 = DefaultTrafficSelector.builder()
                .matchEthDst(MacAddress.valueOf(informationsMap.get("mac-2")));
        FilteredConnectPoint ingress1 = new FilteredConnectPoint(
            ConnectPoint.deviceConnectPoint("of:0000000000000002/1"));
        FilteredConnectPoint egress1 = new FilteredConnectPoint(h2ConnectPoint);

        PointToPointIntent intent1 = PointToPointIntent.builder()
                .appId(appId)
                .selector(selector1.build())
                .treatment(DefaultTrafficTreatment.emptyTreatment())
                .filteredIngressPoint(ingress1)
                .filteredEgressPoint(egress1)
                .build();

        intentService.submit(intent1);
        log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                intent1.filteredIngressPoint().connectPoint().deviceId(),
                intent1.filteredIngressPoint().connectPoint().port(),
                intent1.filteredEgressPoint().connectPoint().deviceId(),
                intent1.filteredEgressPoint().connectPoint().port());

        // s5 to h2
        TrafficSelector.Builder selector3 = DefaultTrafficSelector.builder()
            .matchEthDst(MacAddress.valueOf(informationsMap.get("mac-2")));
        FilteredConnectPoint ingress3 = new FilteredConnectPoint(
            ConnectPoint.deviceConnectPoint("of:0000000000000005/3"));
        FilteredConnectPoint egress3 = new FilteredConnectPoint(h2ConnectPoint);

        PointToPointIntent intent3 = PointToPointIntent.builder()
                .appId(appId)
                .selector(selector3.build())
                .treatment(DefaultTrafficTreatment.emptyTreatment())
                .filteredIngressPoint(ingress3)
                .filteredEgressPoint(egress3)
                .build();

        intentService.submit(intent3);
        log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                intent3.filteredIngressPoint().connectPoint().deviceId(),
                intent3.filteredIngressPoint().connectPoint().port(),
                intent3.filteredEgressPoint().connectPoint().deviceId(),
                intent3.filteredEgressPoint().connectPoint().port());

        // h2 to h1
        TrafficSelector.Builder selector2 = DefaultTrafficSelector.builder()
                .matchEthDst(MacAddress.valueOf(informationsMap.get("mac-1")));
        FilteredConnectPoint ingress2 = new FilteredConnectPoint(h2ConnectPoint);
        FilteredConnectPoint egress2 = new FilteredConnectPoint(h1ConnectPoint);

        PointToPointIntent intent2 = PointToPointIntent.builder()
                .appId(appId)
                .selector(selector2.build())
                .treatment(DefaultTrafficTreatment.emptyTreatment())
                .filteredIngressPoint(ingress2)
                .filteredEgressPoint(egress2)
                .build();

        intentService.submit(intent2);
        log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                intent2.filteredIngressPoint().connectPoint().deviceId(),
                intent2.filteredIngressPoint().connectPoint().port(),
                intent2.filteredEgressPoint().connectPoint().deviceId(),
                intent2.filteredEgressPoint().connectPoint().port());
    }
}
