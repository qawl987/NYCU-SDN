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
package nycu.winlab.proxyarp;

import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onlab.packet.ARP;
import org.onlab.packet.Ethernet;

import java.nio.ByteBuffer;
import java.util.Dictionary;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;

import static org.onlab.util.Tools.get;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = {SomeInterface.class},
           property = {
               "someProperty=Some Default String Value",
           })
public class AppComponent implements SomeInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */
    private String someProperty;

    private ApplicationId appId;

    private ProxyArpPacketProcessor processor = new ProxyArpPacketProcessor();

    private Map<Ip4Address, MacAddress> arpTable = new HashMap<>();

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgePortService;

    @Activate
    protected void activate() {
        cfgService.registerProperties(getClass());
        appId = coreService.registerApplication("nycu.winlab.bridge");
        packetService.addProcessor(processor, PacketProcessor.director(2));
        // Request for IPv4 packets
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();

        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.LOWEST, appId);
        arpTable.put(Ip4Address.valueOf("172.16.11.2"), MacAddress.valueOf("1a:d0:0c:71:f3:c6"));
        arpTable.put(Ip4Address.valueOf("172.16.11.69"), MacAddress.valueOf("9a:56:d9:a9:1b:06"));
        for (Map.Entry<Ip4Address, MacAddress> entry : arpTable.entrySet()) {
            log.info("IP Address: " + entry.getKey() + ", MAC Address: " + entry.getValue());
        }
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

        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
        log.info("Reconfigured");
    }

    @Override
    public void someMethod() {
        log.info("Invoked");
    }

    private class ProxyArpPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            // log.info("LearningBridgeProcessor Handle Packet.");

            if (context.isHandled()) {
                //log.info("Packet has been handled, skip it...");
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPacket = pkt.parsed();

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
                        log.info("TABLE MISS. Send request to edge ports");
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
                        log.info("TABLE HIT. Requested MAC = {}", dstMac.toString());
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

                    log.info("RECV REPLY. Requested MAC = {}", dstMac.toString());
                }
            }
        }
    }
}