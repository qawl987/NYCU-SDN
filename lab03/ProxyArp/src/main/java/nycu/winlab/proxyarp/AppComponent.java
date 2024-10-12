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
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.nio.ByteBuffer;
import java.util.Dictionary;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;

import org.onosproject.net.device.DeviceService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;

import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;

import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;

import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.OutboundPacket;

import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.packet.ARP;
import org.onlab.packet.IpAddress;

import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = {AppComponent.class},
           property = {
           })
public class AppComponent {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    private ArpPacketProcessor processor = new ArpPacketProcessor();
    private ApplicationId appId;
    private Map<IpAddress, MacAddress> arpTable = new HashMap<>();

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nycu.winlab.proxyarp");
        // add a packet processor to packetService
        packetService.addProcessor(processor, 2);

        // install a flowrule for packet-in
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);


        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        packetService.cancelPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP).build(),
                PacketPriority.REACTIVE, appId);
        packetService.removeProcessor(processor);
        processor = null;
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context.getProperties();
        log.info("Reconfigured");
    }

    private class ArpPacketProcessor  implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            if (context.isHandled()) {
                return;
            }
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

            // step3 Proxy ARP learns IP-MAC mappings of the sender
            if (arpTable.get(srcIp) == null) {
                arpTable.put(srcIp, ethPkt.getSourceMAC());
            }

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
                log.info("TABLE MISS. Send request to edge ports");
                // if target address isn't in ARP table, send it to all host except the origin
                // one
                Iterable<Device> devices = deviceService.getDevices();
                for (Device device : devices) {
                    DeviceId deviceId = device.id();
                    for (Port port : deviceService.getPorts(deviceId)) {
                        PortNumber outPort = port.number();
                        if (deviceId.equals(recDevId) && outPort.equals(recPort)) {
                            continue;
                        }

                        OutboundPacket outboundPacket = new DefaultOutboundPacket(deviceId,
                                DefaultTrafficTreatment.builder().setOutput(outPort).build(),
                                ByteBuffer.wrap(ethPkt.serialize()));
                        packetService.emit(outboundPacket);
                    }
                }
            }

            if (opcode == ARP.OP_REPLY) {
                MacAddress senderMacAddress = MacAddress.valueOf(arpPkt.getSenderHardwareAddress());
                MacAddress targetMacAddress = MacAddress.valueOf(arpPkt.getTargetHardwareAddress());
                arpTable.put(srcIp, senderMacAddress);
                log.info("RECV REPLY. Requested MAC = " + senderMacAddress);
                Set<Host> hosts = hostService.getHostsByMac(targetMacAddress);
                if (hosts.isEmpty()) {
                    throw new Error("Can't find host"); // No host found for the given IP address, do nothing
                }
                Host dstHost = hosts.iterator().next();
                DeviceId dstDeviceId = dstHost.location().deviceId();
                PortNumber dstPort = dstHost.location().port();

                OutboundPacket outboundPacket = new DefaultOutboundPacket(dstDeviceId,
                        DefaultTrafficTreatment.builder().setOutput(dstPort).build(),
                        ByteBuffer.wrap(ethPkt.serialize()));
                packetService.emit(outboundPacket);
            }
        }
    }

}
