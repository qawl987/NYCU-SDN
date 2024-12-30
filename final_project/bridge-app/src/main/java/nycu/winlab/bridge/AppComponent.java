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
package nycu.winlab.bridge;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.onlab.util.Tools.get;

import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;

import org.onosproject.core.CoreService;
import org.onosproject.core.ApplicationId;

import org.onosproject.cfg.ComponentConfigService;

import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.edge.EdgePortService;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.ARP;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.packet.DefaultOutboundPacket;
//For IPV6
import org.onlab.packet.IPv6;
import org.onlab.packet.ICMP6;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.ndp.NeighborAdvertisement;
import org.onlab.packet.ndp.NeighborSolicitation;
//import org.onlab.packet.Ip6Prefix;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = {SomeInterface.class},
           property = {
               "someProperty=Some Default String Value",
           })
public class AppComponent implements SomeInterface {

    private final Logger log = LoggerFactory.getLogger("LearningBridge");

    /** Some configurable property. */
    private String someProperty;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    /* For registering the application */
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    /* For handling the packet */
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    /* For installing the flow rule */
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgePortService;

    /* Variables */
    private ApplicationId appId;
    private LearningBridgeProcessor processor = new LearningBridgeProcessor();
    private Map<DeviceId, Map<MacAddress, PortNumber>> bridgeTable = new HashMap<>();
    private Map<Ip4Address, MacAddress> arpTable = new HashMap<>();
    //For IPV6
    private Map<Ip6Address, MacAddress> ndpTable = new HashMap<>();
    private int flowPriority = 30; // Default value
    private int flowTimeout = 30;  // Default value

    @Activate
    protected void activate() {
        // 註冊組件的屬性
        cfgService.registerProperties(getClass());

        // 註冊應用程序並獲取應用程序 ID
        appId = coreService.registerApplication("nycu.winlab.bridge");

        // 添加數據包處理器
        packetService.addProcessor(processor, PacketProcessor.director(2));

        // 構建流量選擇器以請求 IPv4 數據包
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);

        // 請求最低優先級的 IPv4 數據包
        packetService.requestPackets(selector.build(), PacketPriority.LOWEST, appId);
        for (Map.Entry<Ip4Address, MacAddress> entry : arpTable.entrySet()) {
            log.info("IP Address: " + entry.getKey() + ", MAC Address: " + entry.getValue());
        }
        // 添加IPv6配置
        TrafficSelector.Builder selectorIPv6 = DefaultTrafficSelector.builder();
        selectorIPv6.matchEthType(Ethernet.TYPE_IPV6);
        packetService.requestPackets(selectorIPv6.build(), PacketPriority.LOWEST, appId);
        arpTable.put(Ip4Address.valueOf("172.16.11.254"), MacAddress.valueOf("00:00:00:00:00:11"));
        arpTable.put(Ip4Address.valueOf("192.168.63.1"), MacAddress.valueOf("22:32:dd:a7:9b:7e"));
        arpTable.put(Ip4Address.valueOf("192.168.70.11"), MacAddress.valueOf("22:32:dd:a7:9b:7e"));
        ndpTable.put(Ip6Address.valueOf("2a0b:4e07:c4:11::68"), MacAddress.valueOf("00:00:00:00:00:11"));
        ndpTable.put(Ip6Address.valueOf("fd63::1"), MacAddress.valueOf("22:32:dd:a7:9b:7e"));
        ndpTable.put(Ip6Address.valueOf("fd70::11"), MacAddress.valueOf("22:32:dd:a7:9b:7e"));
        log.info("Started {}", appId.id());
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        packetService.removeProcessor(processor);
        // Cancel the request for IPv4 packets
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();

        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.LOWEST, appId);
        // 添加IPv6配置
        TrafficSelector.Builder selectorIPv6 = DefaultTrafficSelector.builder();
        selectorIPv6.matchEthType(Ethernet.TYPE_IPV6);
        packetService.cancelPackets(selectorIPv6.build(), PacketPriority.LOWEST, appId);
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
    }

    @Override
    public void someMethod() {
        log.info("Invoked");
    }

    /* Send out the packet from the specified port */
    private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }

    /* Broadcast the packet */
    private void flood(PacketContext context) {
        packetOut(context, PortNumber.FLOOD);
    }

    /* Install Flow Rule */
    private void installRule(PacketContext context, PortNumber dstProt) {
        InboundPacket packet = context.inPacket();
        Ethernet ethPacket = packet.parsed();
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        TrafficTreatment treatment = context.treatmentBuilder().setOutput(dstProt).build();

        // Match Src and Dst MAC Address
        selectorBuilder.matchEthDst(ethPacket.getDestinationMAC());
        selectorBuilder.matchEthSrc(ethPacket.getSourceMAC());

        // Create Flow Rule
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())          // Build the selector
                .withTreatment(treatment)                       // Setup the treatment
                .withPriority(flowPriority)                     // Setup the priority of flow
                .withFlag(ForwardingObjective.Flag.VERSATILE)   // Matches two or more header fields.
                .fromApp(appId)                                 // Specify from which application
                .makeTemporary(flowTimeout)                     // Set timeout
                .add();                                         // Build the flow rule
        // log.info("Flow Rule {}", forwardingObjective);

        // Install the flow rule on the specified switch
        flowObjectiveService.forward(packet.receivedFrom().deviceId(), forwardingObjective);

        // After install the flow rule, use packet-out message to send packet
        packetOut(context, dstProt);
    }

    private void installRlueByRule(PacketContext context, PortNumber dstProt) {
        // InboundPacket pkt = context.inPacket();
        // Ethernet ethPacket = pkt.parsed();

        // TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder()
        //         .matchEthDst(ethPacket.getDestinationMAC())
        //         .matchEthSrc(ethPacket.getSourceMAC());

        // // 添加IPv6特定的匹配条件
        // if (ethPacket.getEtherType() == Ethernet.TYPE_IPV6) {
        //     selectorBuilder.matchEthType(Ethernet.TYPE_IPV6);
        //     // 获取IPv6包
        //     IPv6 ipv6Packet = (IPv6) ethPacket.getPayload();
        //     // 匹配IPv6源地址和目标地址
        //     selectorBuilder.matchIPv6Src(Ip6Prefix.valueOf(ipv6Packet.getSourceAddress(),
        //                     Ip6Prefix.MAX_MASK_LENGTH))
        //                 .matchIPv6Dst(Ip6Prefix.valueOf(ipv6Packet.getSourceAddress(),
        //                     Ip6Prefix.MAX_MASK_LENGTH));
        // }
        // // 原有的IPv4处理
        // else if (ethPacket.getEtherType() == Ethernet.TYPE_IPV4) {
        //     selectorBuilder.matchEthType(Ethernet.TYPE_IPV4);
        // }
        FlowRule flowRule = DefaultFlowRule.builder()
                .forDevice(context.inPacket().receivedFrom().deviceId())
                .withSelector(DefaultTrafficSelector.builder()
                        .matchEthDst(context.inPacket().parsed().getDestinationMAC())
                        .matchEthSrc(context.inPacket().parsed().getSourceMAC())
                        .build())
                .withTreatment(DefaultTrafficTreatment.builder()
                        .setOutput(dstProt)
                        .build())
                .withPriority(flowPriority)
                .fromApp(appId)
                .makeTemporary(flowTimeout)
                .build();
        flowRuleService.applyFlowRules(flowRule);
        packetOut(context, dstProt);

    }

    /* Handle the packets coming from switchs */
    private class LearningBridgeProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            // log.info("LearningBridgeProcessor Handle Packet.");

            if (context.isHandled()) {
                //log.info("Packet has been handled, skip it...");
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPacket = pkt.parsed();

            if (ethPacket == null) {
                // log.error("Packet type is not ethernet");
                return;
            }

            if (ethPacket.getEtherType() == Ethernet.TYPE_LLDP || ethPacket.getEtherType() == Ethernet.TYPE_BSN) {
                // log.info("Ignore LLDP or BDDP packet");
                return;
            }


            if (ethPacket.getEtherType() == Ethernet.TYPE_ARP) {
                ARP arpPacket = (ARP) ethPacket.getPayload();
                short opCode = arpPacket.getOpCode();
                    if (opCode == ARP.OP_REQUEST) {
                    Ip4Address srcAddr  = Ip4Address.valueOf(arpPacket.getSenderProtocolAddress());
                    Ip4Address dstAddr  = Ip4Address.valueOf(arpPacket.getTargetProtocolAddress());
                    MacAddress srcMac = ethPacket.getSourceMAC();
                    MacAddress dstMac = arpTable.get(dstAddr);

                    if (arpTable.get(srcAddr) == null) {
                        arpTable.put(srcAddr, srcMac);
                    }
                    if (dstMac == null) {
                        // log.info("TABLE MISS. Send request to edge ports");
                        //TODO: packOut to edge ports
                        for (ConnectPoint cp : edgePortService.getEdgePoints()) {
                            if (cp.equals(pkt.receivedFrom())) {
                                continue;
                            } else {
                                packetService.emit(new DefaultOutboundPacket(
                                    cp.deviceId(),
                                    DefaultTrafficTreatment.builder().setOutput(cp.port()).build(),
                                    ByteBuffer.wrap(ethPacket.serialize())
                                ));
                            }
                        }

                    } else {
                        // log.info("TABLE HIT. Requested MAC = {}", dstMac.toString());
                        Ethernet arpReply = ARP.buildArpReply(dstAddr, dstMac, ethPacket);
                        packetService.emit(new DefaultOutboundPacket(
                            pkt.receivedFrom().deviceId(),
                            DefaultTrafficTreatment.builder().setOutput(pkt.receivedFrom().port()).build(),
                            ByteBuffer.wrap(arpReply.serialize())
                        ));

                    }
                } else if (opCode == ARP.OP_REPLY) {
                    Ip4Address srcAddr = Ip4Address.valueOf(arpPacket.getSenderProtocolAddress());
                    MacAddress srcMac = ethPacket.getSourceMAC();
                    MacAddress dstMac = ethPacket.getDestinationMAC();

                    if (arpTable.get(srcAddr) == null) {
                        arpTable.put(srcAddr, srcMac);
                    }
                    for (Map.Entry<Ip4Address, MacAddress> entry : arpTable.entrySet()) {
                        log.info("IP Address: " + entry.getKey() + ", MAC Address: " + entry.getValue());
                    }

                    // log.info("RECV REPLY. Requested MAC = {}", dstMac.toString());
                }
                return;
            }
            // 对于IPv6的NDP包处理
            if (ethPacket.getEtherType() == Ethernet.TYPE_IPV6) {
                //log.info("Receive IPV6 Type");
                IPv6 ipv6Packet = (IPv6) ethPacket.getPayload();
                if (ipv6Packet.getNextHeader() == IPv6.PROTOCOL_ICMP6) {
                    ICMP6 icmp6Packet = (ICMP6) ipv6Packet.getPayload();

                    // 处理邻居请求包(Neighbor Solicitation)
                    if (icmp6Packet.getIcmpType() == ICMP6.NEIGHBOR_SOLICITATION) {
                        handleNeighborSolicitation(context, ethPacket, ipv6Packet, icmp6Packet);
                    }

                    // 处理邻居通告包(Neighbor Advertisement)
                    if (icmp6Packet.getIcmpType() == ICMP6.NEIGHBOR_ADVERTISEMENT) {
                        handleNeighborAdvertisement(context, ethPacket, ipv6Packet, icmp6Packet);
                    }
                }
            }




            // MacAddress srcMac = ethPacket.getSourceMAC();
            // MacAddress dstMac = ethPacket.getDestinationMAC();
            // DeviceId receivedID = pkt.receivedFrom().deviceId();

            // if (bridgeTable.get(receivedID) == null) {
            //     bridgeTable.put(receivedID, new HashMap<>());
            // }
            // if (bridgeTable.get(receivedID).get(srcMac) == null) {
            //     Map<MacAddress, PortNumber> switchMap = bridgeTable.get(receivedID);
            //     switchMap.put(srcMac, context.inPacket().receivedFrom().port());
            //     log.info("Check switchMap context: " + context.inPacket().receivedFrom().port());
            //     bridgeTable.put(receivedID, switchMap);
            //     log.info("Add an entry to the port table of `{}`. MAC address: `{}` => Port: `{}`.",
            //             receivedID, srcMac, context.inPacket().receivedFrom().port());
            // }

            // PortNumber dstPort = bridgeTable.get(receivedID).get(dstMac);

            // if (dstPort == null) {
            //     if (receivedID == DeviceId.deviceId("of:0000000000000001") ||
            //     receivedID == DeviceId.deviceId("of:0000000000000002")) {
            //         log.info("MAC address `{}` is missed on `{}`. Flood the packet.", dstMac, receivedID);
            //     }
            //     flood(context);
            // } else {
            //     // Table Hit, Install rule
            //     log.info("MAC address `{}` is matched on `{}`. Install a flow rule.", dstMac, receivedID);
            //     // installRule(context, dstPort);
            //     installRlueByRule(context, dstPort);
            // }
        }
    }

    private void handleNeighborSolicitation(PacketContext context, Ethernet ethPacket,
                                      IPv6 ipv6Packet, ICMP6 icmp6Packet) {
        // 处理邻居请求
        NeighborSolicitation nsPacket = (NeighborSolicitation) icmp6Packet.getPayload();
        MacAddress srcMac = ethPacket.getSourceMAC();
        Ip6Address srcIPv6 = Ip6Address.valueOf(ipv6Packet.getSourceAddress());

        // 存储IPv6和MAC的映射关系
        ndpTable.put(srcIPv6, srcMac);

        // 如果目标地址在我们的表中，发送邻居通告
        Ip6Address targetAddress = Ip6Address.valueOf(nsPacket.getTargetAddress());
        MacAddress targetMac = ndpTable.get(targetAddress);

        if (targetMac != null) {
            // 构建NA包
            // Ethernet naEth = new Ethernet();
            // naEth.setSourceMACAddress(targetMac)
            //     .setDestinationMACAddress(ethPacket.getSourceMAC())
            //     .setEtherType(Ethernet.TYPE_IPV6);

            // IPv6 naIPv6 = new IPv6();
            // naIPv6.setSourceAddress(targetAddress.toOctets())
            //     .setDestinationAddress(srcIPv6.toOctets())
            //     .setNextHeader(IPv6.PROTOCOL_ICMP6)
            //     .setHopLimit((byte) 255);

            // ICMP6 naIcmp6 = new ICMP6();
            // naIcmp6.setIcmpType(ICMP6.NEIGHBOR_ADVERTISEMENT)
            //     .setIcmpCode((byte) 0);

            // NeighborAdvertisement na = new NeighborAdvertisement();
            // na.setTargetAddress(targetAddress.toOctets())
            // .setRouterFlag((byte) 1)
            // .setSolicitedFlag((byte) 1)
            // .setOverrideFlag((byte) 1);

            // naIcmp6.setPayload(na);
            // naIPv6.setPayload(naIcmp6);
            // naEth.setPayload(naIPv6);
            InboundPacket pkt = context.inPacket();
            Ethernet ethPktReply = NeighborAdvertisement.buildNdpAdv(targetAddress,
             ndpTable.get(targetAddress), ethPacket);
            IPv6 ipv6Reply = (IPv6) ethPktReply.getPayload();
            ipv6Reply.setHopLimit((byte) 255);
            ethPktReply.setPayload(ipv6Reply);
            byte[] serializedPacket = ethPktReply.serialize();

            // Emit the packet using PacketService
            packetService.emit(new DefaultOutboundPacket(
                pkt.receivedFrom().deviceId(),
                DefaultTrafficTreatment.builder()
                    .setOutput(pkt.receivedFrom().port())  // Send it back to the input port
                    .build(),
                ByteBuffer.wrap(serializedPacket)         // Serialized Ethernet packet
            ));
            // packetService.emit(new DefaultOutboundPacket(
            //     context.inPacket().receivedFrom().deviceId(),
            //     DefaultTrafficTreatment.builder().setOutput(context.inPacket().receivedFrom().port()).build(),
            //     ByteBuffer.wrap(naEth.serialize())
            // ));
            // packetOut(ethPktReply, pkt.receivedFrom().deviceId(), pkt.receivedFrom().port());
            //     log.info("TABLE HIT. Requested MAC = " + ndpTable.get(targetAddress) + " for " + targetAddress);
        } else {
            // 如果不知道目标MAC地址，洪泛到所有边缘端口
            flood(context);
        }
    }

    private void handleNeighborAdvertisement(PacketContext context, Ethernet ethPacket,
                                        IPv6 ipv6Packet, ICMP6 icmp6Packet) {
        // 处理邻居通告
        NeighborAdvertisement naPacket = (NeighborAdvertisement) icmp6Packet.getPayload();
        MacAddress srcMac = ethPacket.getSourceMAC();
        Ip6Address srcIPv6 = Ip6Address.valueOf(ipv6Packet.getSourceAddress());
        for (Map.Entry<Ip6Address, MacAddress> entry : ndpTable.entrySet()) {
                        log.info("IPV6 IP Address: " + entry.getKey() + ", MAC Address: " + entry.getValue());
                    }

        // 更新NDP表
        ndpTable.put(srcIPv6, srcMac);
    }
}