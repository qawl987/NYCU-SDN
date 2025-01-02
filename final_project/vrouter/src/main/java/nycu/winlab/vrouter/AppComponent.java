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

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.packet.IPv4;
import org.onlab.packet.IPv6;
import org.onlab.packet.TCP;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;


import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.FilteredConnectPoint;

import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowRule;

import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.intent.MultiPointToSinglePointIntent;
import org.onosproject.net.intent.IntentService;
// import org.onosproject.net.intent.Intent;

import org.onosproject.net.intf.Interface;
import org.onosproject.net.intf.InterfaceService;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;

import org.onosproject.routeservice.RouteService;
import org.onosproject.routeservice.ResolvedRoute;
import org.onosproject.routeservice.RouteTableId;
import org.onosproject.routeservice.RouteInfo;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;

import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Set;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
// import java.util.List;

import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.FlowRuleService;
// IPv6
import org.onlab.packet.Ip6Address;
// import java.util.Optional;
// import org.onlab.packet.ICMP6;
/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = AppComponent.class
)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final VRouterConfigListener vRouterListener = new VRouterConfigListener();
    private final ConfigFactory<ApplicationId, VRouterConfig> factory = new ConfigFactory<ApplicationId, VRouterConfig>(
        APP_SUBJECT_FACTORY, VRouterConfig.class, "router") {
        @Override
        public VRouterConfig createConfig() {
            return new VRouterConfig();
        }
    };

   @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

     @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected InterfaceService intfService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected RouteService routeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;
    private Map<DeviceId, Map<MacAddress, PortNumber>> bridgeTable = new HashMap<>();
    ApplicationId appId;
    ArrayList<ConnectPoint> vrroutingCPs;
    MacAddress vrroutingMac;
    MacAddress virtualMac;
    IpAddress virtualIP;
    Ip6Address virtualIP6;
    ArrayList<IpAddress> peers = new ArrayList<IpAddress>();
    ArrayList<Ip6Address> peers6 = new ArrayList<Ip6Address>();
    ArrayList<ConnectPoint> peerCPs = new ArrayList<ConnectPoint>();

    private VRouterPacketProcessor vRouterProcessor = new VRouterPacketProcessor();
    private Map<Ip6Address, MacAddress> ndpTable = new HashMap<>();
    private int flowPriority = 30; // Default value
    private int flowTimeout = 30;  // Default value
    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nycu.winlab.vrouter");
        packetService.addProcessor(vRouterProcessor, PacketProcessor.director(6));
        cfgService.addListener(vRouterListener);
        cfgService.registerConfigFactory(factory);
        // 構建流量選擇器以請求 IPv4 數據包
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);

        // 請求最低優先級的 IPv4 數據包
        packetService.requestPackets(selector.build(), PacketPriority.LOWEST, appId);
        // 添加IPv6配置
        TrafficSelector.Builder selectorIPv6 = DefaultTrafficSelector.builder();
        selectorIPv6.matchEthType(Ethernet.TYPE_IPV6);
        packetService.requestPackets(selectorIPv6.build(), PacketPriority.LOWEST, appId);
        log.info("Started: " + appId.name());
        peerCPs.add(ConnectPoint.fromString("of:0000000000000001/5"));
        peerCPs.add(ConnectPoint.fromString("of:00008eaa1988954b/3"));
    }

    @Deactivate
    protected void deactivate() {
        cancelPacketsIn();
        cfgService.removeListener(vRouterListener);
        cfgService.unregisterConfigFactory(factory);
        packetService.removeProcessor(vRouterProcessor);
        vRouterProcessor = null;

        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        requestPacketsIn();
    }

    private void requestPacketsIn() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    private void cancelPacketsIn() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

     private class VRouterConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            /* read config file */
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED) &&
             event.configClass().equals(VRouterConfig.class)) {
                VRouterConfig config = cfgService.getConfig(appId, VRouterConfig.class);
                if (config != null) {
                    vrroutingCPs = config.getVrroutingCP();
                    vrroutingMac = config.getVrroutingMac();
                    virtualMac = config.getVirtualMac();
                    virtualIP = config.getVirtualIP();
                    virtualIP6 = config.getVirtualIP6();
                    peers = config.getPeers();
                    peers6 = config.getPeers6();
                    for (int i = 0; i < vrroutingCPs.size(); i++) {
                        ConnectPoint vrroutingCP = vrroutingCPs.get(i);
                        IpAddress peerIp = peers.get(i);
                        Ip6Address peerIp6 = peers6.get(i);
                        log.info("Vrrouting is connected to: " + vrroutingCP);
                        log.info("Peer: " + peers.get(i));
                        log.info("Peer6: " + peers6.get(i));
                        Interface peerIntf = intfService.getMatchingInterface(peerIp);
                        Interface peerIntf6 = intfService.getMatchingInterface(peerIp6);
                        ConnectPoint peerCP = peerIntf.connectPoint();
                        /* peer --> quagga */
                        IpAddress speakerIp = peerIntf.ipAddressesList().get(0).ipAddress();
                        bgpIntent(peerCP, vrroutingCP, speakerIp);

                        /* peer <-- quagga */
                        bgpIntent(vrroutingCP, peerCP, peerIp);

                        // IPv6
                        ConnectPoint peerCP6 = peerIntf6.connectPoint();
                        /* peer --> quagga */
                        IpAddress speakerIp6 = peerIntf6.ipAddressesList().get(1).ipAddress();
                        bgpIntent6(peerCP6, vrroutingCP, speakerIp6);

                        /* peer <-- quagga */
                        bgpIntent6(vrroutingCP, peerCP6, peerIp6);
                    }
                    /* Log Info */
                    log.info("Vrrouting-mac: " + vrroutingMac);
                    log.info("Virtual-mac: " + virtualMac);
                    log.info("Virtual-ip: " + virtualIP);
                    log.info("Virtual-ip6: " + virtualIP6);
                }
            }
        }

        private void bgpIntent(ConnectPoint ingress, ConnectPoint egress, IpAddress dstIp) {
            /* install intent for BGP packet */
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPDst(dstIp.toIpPrefix());

            TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();

            FilteredConnectPoint ingressPoint = new FilteredConnectPoint(ingress);
            FilteredConnectPoint egressPoint = new FilteredConnectPoint(egress);

            log.info("[BGP] `" + ingress + " => " + egress + " is submitted.");

            PointToPointIntent intent = PointToPointIntent.builder()
                        .filteredIngressPoint(ingressPoint)
                        .filteredEgressPoint(egressPoint)
                        .selector(selector.build())
                        .treatment(treatment)
                        .priority(40)
                        .appId(appId)
                        .build();
            intentService.submit(intent);
        }

        private void bgpIntent6(ConnectPoint ingress, ConnectPoint egress, IpAddress dstIp) {
            /* install intent for BGP packet */
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV6)
                        .matchIPv6Dst(dstIp.toIpPrefix());

            TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();

            FilteredConnectPoint ingressPoint = new FilteredConnectPoint(ingress);
            FilteredConnectPoint egressPoint = new FilteredConnectPoint(egress);

            log.info("[BGP6] `" + ingress + " => " + egress + " is submitted.");

            PointToPointIntent intent = PointToPointIntent.builder()
                        .filteredIngressPoint(ingressPoint)
                        .filteredEgressPoint(egressPoint)
                        .selector(selector.build())
                        .treatment(treatment)
                        .priority(40)
                        .appId(appId)
                        .build();
            intentService.submit(intent);
        }
    }


    private class VRouterPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            if (context.isHandled() || context.inPacket().parsed().getEtherType() == Ethernet.TYPE_ARP) {
                return;
            }
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            MacAddress dstMac = ethPkt.getDestinationMAC();
            ConnectPoint srcCP = pkt.receivedFrom();
            log.error("srcCp: " + srcCP);
            if (!(ethPkt.getEtherType() == Ethernet.TYPE_IPV6)
                && !(ethPkt.getEtherType() == Ethernet.TYPE_IPV4)) {
                return;
            }
            // if (ethPkt.getEtherType() == Ethernet.TYPE_IPV6) {
            //     IPv6 ipv6Packet = (IPv6) ethPkt.getPayload();
            //     if (ipv6Packet.getNextHeader() == IPv6.PROTOCOL_ICMP6) {
            //         ICMP6 icmp6Packet = (ICMP6) ipv6Packet.getPayload();
            //         if (icmp6Packet.getIcmpType() == ICMP6.NEIGHBOR_SOLICITATION
            //         || icmp6Packet.getIcmpType() == ICMP6.NEIGHBOR_ADVERTISEMENT) {
            //             return;
            //         }
            //     }
            // }
            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV6) {
                IPv6 ipv6Pkt = (IPv6) ethPkt.getPayload();
                Ip6Address dstIp = Ip6Address.valueOf(ipv6Pkt.getDestinationAddress());
                log.info("=== In IPV6 VRouterPacketProcessor ===");
                log.info("DstIp: " + dstIp);
                log.info("DstMac: " + dstMac);
                /* 先判斷 packet 是從裡面出去，還是從外面進來的 */
                if (dstMac.equals(virtualMac)) {             // packet 從裡面要出去的
                    /* 那就從 RouteService 去找路徑，看要把 packet 往哪邊送 */
                    ResolvedRoute bestRoute = getBestRoute6(dstIp);
                    log.info("bestRoute: " + bestRoute);
                    if (bestRoute != null) {            // 有找到路徑 (知道怎麼走)
                        // IpPrefix dstPrefix = bestRoute.prefix();
                        IpAddress nextHopIp = bestRoute.nextHop();
                        MacAddress nextHopMac = bestRoute.nextHopMac();
                        log.error("next hop ip:" + nextHopIp + "next hop mac: " + nextHopMac);
                        Set<Host> dstHost = hostService.getHostsByIp(nextHopIp);
                        for (Host host : dstHost) {
                            log.error("Host: " + host);
                        }
                        if (dstHost.size() > 0) {
                            Host host = new ArrayList<Host>(dstHost).get(0);
                            ConnectPoint outCP = ConnectPoint.fromString(host.location().toString());
                            log.error("outCP: " + outCP);
                            sdnExternalIntent6(context, srcCP, outCP, vrroutingMac, nextHopMac);
                        }
                        log.error("No nextHop host find.");
                        context.block();
                        return;
                    }
                } else if (dstMac.equals(vrroutingMac)) {         // packet 從外面進來的
                    /* 接著判斷是: 外往外，外往內 */
                    Set<Host> dstHost = hostService.getHostsByIp(dstIp);
                    if (dstHost.size() > 0) {           // 有找到 host，代表是外往內
                        Host host = new ArrayList<Host>(dstHost).get(0);
                        ConnectPoint hostCP = ConnectPoint.fromString(host.location().toString());
                        MacAddress hostMac = host.mac();

                        sdnExternalIntent6(context, srcCP, hostCP, virtualMac, hostMac);
                        context.block();
                        return;
                    } else {                              // 找不到 host有兩種情況: 1.host 不存在，2. host在其他網段
                        /* 就用 RouteService 去看，有沒有到達 host network 的路徑 */
                        ResolvedRoute bestRoute = getBestRoute6(dstIp);
                        if (bestRoute != null) {        // 有找到路徑
                            IpAddress nextHopIp = bestRoute.nextHop();
                            MacAddress nextHopMac = bestRoute.nextHopMac();

                            Set<Host> findHost = hostService.getHostsByIp(nextHopIp);
                            if (findHost.size() > 0) {
                                Host host = new ArrayList<Host>(findHost).get(0);
                                ConnectPoint outCP = ConnectPoint.fromString(host.location().toString());
                                log.error("outCP: " + outCP);
                                externalExternalIntent6(context, outCP, vrroutingMac, nextHopMac);
                            }
                            log.error("E2E No next hop host find.");
                            context.block();
                            return;
                        }
                    }
                }
            }
            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                IPv4 ipv4Pkt = (IPv4) ethPkt.getPayload();
                IpAddress dstIp = IpAddress.valueOf(ipv4Pkt.getDestinationAddress());

                if (ipv4Pkt.getProtocol() == IPv4.PROTOCOL_TCP) {
                    TCP tcpPkt = (TCP) ipv4Pkt.getPayload();
                    if (tcpPkt.getDestinationPort() == 179) {
                        // log.info("[*] Get PGB Packet");
                        return;
                    }
                }
                log.info("=== In IPV4 VRouterPacketProcessor ===");
                log.info("DstIp: " + dstIp);
                log.info("DstMac: " + dstMac);
                /* 先判斷 packet 是從裡面出去，還是從外面進來的 */
                if (dstMac.equals(virtualMac)) {             // packet 從裡面要出去的
                    /* 那就從 RouteService 去找路徑，看要把 packet 往哪邊送 */
                    ResolvedRoute bestRoute = getBestRoute(dstIp);
                    if (bestRoute != null) {            // 有找到路徑 (知道怎麼走)
                        // IpPrefix dstPrefix = bestRoute.prefix();
                        IpAddress nextHopIp = bestRoute.nextHop();
                        MacAddress nextHopMac = bestRoute.nextHopMac();
                        log.error("next hop ip:" + nextHopIp + "next hop mac: " + nextHopMac);
                        Set<Host> dstHost = hostService.getHostsByIp(nextHopIp);
                        for (Host host : dstHost) {
                            log.error("Host: " + host);
                        }
                        if (dstHost.size() > 0) {
                            Host host = new ArrayList<Host>(dstHost).get(0);
                            ConnectPoint outCP = ConnectPoint.fromString(host.location().toString());
                            log.error("outCP: " + outCP);
                            sdnExternalIntent(context, srcCP, outCP, vrroutingMac, nextHopMac);
                        }
                        log.error("No nextHop host find.");
                        context.block();
                        return;
                    }
                } else if (dstMac.equals(vrroutingMac)) {         // packet 從外面進來的
                    /* 接著判斷是: 外往外，外往內 */
                    Set<Host> dstHost = hostService.getHostsByIp(dstIp);
                    if (dstHost.size() > 0) {           // 有找到 host，代表是外往內
                        Host host = new ArrayList<Host>(dstHost).get(0);
                        ConnectPoint hostCP = ConnectPoint.fromString(host.location().toString());
                        MacAddress hostMac = host.mac();

                        sdnExternalIntent(context, srcCP, hostCP, virtualMac, hostMac);
                        context.block();
                        return;
                    } else {                              // 找不到 host有兩種情況: 1.host 不存在，2. host在其他網段
                        /* 就用 RouteService 去看，有沒有到達 host network 的路徑 */
                        ResolvedRoute bestRoute = getBestRoute(dstIp);
                        if (bestRoute != null) {        // 有找到路徑
                            IpAddress nextHopIp = bestRoute.nextHop();
                            MacAddress nextHopMac = bestRoute.nextHopMac();

                            Set<Host> findHost = hostService.getHostsByIp(nextHopIp);
                            if (findHost.size() > 0) {
                                Host host = new ArrayList<Host>(findHost).get(0);
                                ConnectPoint outCP = ConnectPoint.fromString(host.location().toString());
                                log.error("outCP: " + outCP);
                                externalExternalIntent(context, outCP, vrroutingMac, nextHopMac);
                            }
                            log.error("E2E No next hop host find.");
                            context.block();
                            return;
                        }
                    }
                }
            }
            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV6) {
                IPv6 ipv6Pkt = (IPv6) ethPkt.getPayload();
                Ip6Address dstIp = Ip6Address.valueOf(ipv6Pkt.getDestinationAddress());
                if (!isRelevantPrefix6(dstIp)) {
                    context.block();
                    return;
                }
            }
            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                IPv4 ipv4Pkt = (IPv4) ethPkt.getPayload();
                IpAddress dstIp = IpAddress.valueOf(ipv4Pkt.getDestinationAddress());
                if (!isRelevantPrefix(dstIp)) {
                    context.block();
                    return;
                }
            }
            // Bridge app
            Ethernet ethPacket = pkt.parsed();
            MacAddress srcMac = ethPacket.getSourceMAC();
            DeviceId receivedID = pkt.receivedFrom().deviceId();

            if (bridgeTable.get(receivedID) == null) {
                bridgeTable.put(receivedID, new HashMap<>());
            }
            if (bridgeTable.get(receivedID).get(srcMac) == null) {
                Map<MacAddress, PortNumber> switchMap = bridgeTable.get(receivedID);
                switchMap.put(srcMac, context.inPacket().receivedFrom().port());
                log.info("Check switchMap context: " + context.inPacket().receivedFrom().port());
                bridgeTable.put(receivedID, switchMap);
                log.info("Add an entry to the port table of `{}`. MAC address: `{}` => Port: `{}`.",
                        receivedID, srcMac, context.inPacket().receivedFrom().port());
            }
            PortNumber dstPort = bridgeTable.get(receivedID).get(dstMac);
            if (dstPort == null) {
                if (receivedID == DeviceId.deviceId("of:0000000000000001") ||
                receivedID == DeviceId.deviceId("of:0000000000000002")) {
                    log.info("MAC address `{}` is missed on `{}`. Flood the packet.", dstMac, receivedID);
                }
                flood(context);
            } else {
                // Table Hit, Install rule
                log.info("MAC address `{}` is matched on `{}`. Install a flow rule.", dstMac, receivedID);
                // installRule(context, dstPort);
                installRlueByRule(context, dstPort);
            }
        }

        private ResolvedRoute getBestRoute(IpAddress targetIp) {
            Collection<RouteTableId> routingTable = routeService.getRouteTables();
            for (RouteTableId tableID : routingTable) {
                for (RouteInfo info : routeService.getRoutes(tableID)) {
                    ResolvedRoute bestRoute = info.bestRoute().get();

                    IpPrefix dstPrefix = bestRoute.prefix();                /* 要到的 Ip Network */
                    if (dstPrefix.contains(targetIp)) {
                        return bestRoute;
                    }
                }
            }
            return null;
        }

        private ResolvedRoute getBestRoute6(Ip6Address targetIp) {
            Collection<RouteTableId> routingTable = routeService.getRouteTables();
            for (RouteInfo info : routeService.getRoutes(new RouteTableId("ipv6"))) {
                Collection<ResolvedRoute> resolve = routeService.getAllResolvedRoutes(targetIp.toIpPrefix());
                ResolvedRoute bestRoute = info.bestRoute().orElse(null);
                for (ResolvedRoute r : resolve) {
                    log.info("resolve route: " + r);
                }
                log.info("info: " + info);
                if (bestRoute != null) {
                    log.info("Best Route: " + bestRoute);
                    log.info("I want to find " + targetIp);
                    IpPrefix dstPrefix = bestRoute.prefix();
                    if (dstPrefix.contains(targetIp)) {
                        return bestRoute;
                    }
                } else {
                    log.error("No best route found getBestRoute6.");
                }
            }
            return null;
            // log.info("搜尋 IPv6 路由，目標 IP: {}", targetIp);
            // Optional<ResolvedRoute> bestRoute = routeService.longestPrefixLookup(targetIp);
            // if (bestRoute.isPresent()) {
            //     log.info("找到路由: {} via {}", bestRoute.get().prefix(), bestRoute.get().nextHop());
            // } else {
            //     log.warn("找不到通往 {} 的路由", targetIp);
            // }
            // return bestRoute.orElse(null);
        }
    }

    private void sdnExternalIntent(PacketContext context, ConnectPoint ingress,
     ConnectPoint egress, MacAddress srcMac, MacAddress dstMac) {
        IPv4 ipv4Pkt = (IPv4) context.inPacket().parsed().getPayload();
        IpAddress dstIp = IpAddress.valueOf(ipv4Pkt.getDestinationAddress());
        // Ip4Prefix ip4Prefix = Ip4Prefix.valueOf(ipv4Pkt.getDestinationAddress(), Ip4Prefix.MAX_MASK_LENGTH);

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPDst(dstIp.toIpPrefix());

        /* 要把 src MAC 跟 dst MAC 換掉 */
        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder()
                    .setEthSrc(srcMac)
                    .setEthDst(dstMac);

        FilteredConnectPoint ingressPoint = new FilteredConnectPoint(ingress);
        FilteredConnectPoint egressPoint = new FilteredConnectPoint(egress);

        log.info("[SDN_External] " + ingress + " => " + egress + " is submitted.");

        PointToPointIntent intent = PointToPointIntent.builder()
                    .filteredIngressPoint(ingressPoint)
                    .filteredEgressPoint(egressPoint)
                    .selector(selector.build())
                    .treatment(treatment.build())
                    .priority(50)
                    .appId(appId)
                    .build();
        intentService.submit(intent);
    }

    private void externalExternalIntent(PacketContext context,
     ConnectPoint egress, MacAddress srcMac, MacAddress dstMac) {
        IPv4 ipv4Pkt = (IPv4) context.inPacket().parsed().getPayload();
        IpAddress dstIp = IpAddress.valueOf(ipv4Pkt.getDestinationAddress());
        IpPrefix ip4Prefix = routeService.longestPrefixLookup(dstIp).get().prefix();

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPDst(ip4Prefix);

        /* 要把 src MAC 跟 dst MAC 換掉 */
        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder()
                    .setEthSrc(srcMac)
                    .setEthDst(dstMac);

        Set<FilteredConnectPoint> ingressPoints = new HashSet<FilteredConnectPoint>();
        FilteredConnectPoint egressPoint = new FilteredConnectPoint(egress);
        for (ConnectPoint cp : peerCPs) {
            if (!cp.equals(egress)) {
                ingressPoints.add(new FilteredConnectPoint(cp));
                log.info("[External_External] " + cp + " => " + egress + " is submitted.");
            }
        }

        MultiPointToSinglePointIntent intent = MultiPointToSinglePointIntent.builder()
                    .filteredIngressPoints(ingressPoints)
                    .filteredEgressPoint(egressPoint)
                    .selector(selector.build())
                    .treatment(treatment.build())
                    .priority(60)
                    .appId(appId)
                    .build();
        intentService.submit(intent);
    }

    private void sdnExternalIntent6(PacketContext context, ConnectPoint ingress,
    ConnectPoint egress, MacAddress srcMac, MacAddress dstMac) {
       IPv6 ipv6Pkt = (IPv6) context.inPacket().parsed().getPayload();
       Ip6Address dstIp = Ip6Address.valueOf(ipv6Pkt.getDestinationAddress());
       // Ip4Prefix ip4Prefix = Ip4Prefix.valueOf(ipv4Pkt.getDestinationAddress(), Ip4Prefix.MAX_MASK_LENGTH);

       TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                   .matchEthType(Ethernet.TYPE_IPV6)
                   .matchIPv6Dst(dstIp.toIpPrefix());

       /* 要把 src MAC 跟 dst MAC 換掉 */
       TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder()
                   .setEthSrc(srcMac)
                   .setEthDst(dstMac);

       FilteredConnectPoint ingressPoint = new FilteredConnectPoint(ingress);
       FilteredConnectPoint egressPoint = new FilteredConnectPoint(egress);

       log.info("[SDN_External] " + ingress + " => " + egress + " is submitted.");

       PointToPointIntent intent = PointToPointIntent.builder()
                   .filteredIngressPoint(ingressPoint)
                   .filteredEgressPoint(egressPoint)
                   .selector(selector.build())
                   .treatment(treatment.build())
                   .priority(50)
                   .appId(appId)
                   .build();
       intentService.submit(intent);
   }

   private void externalExternalIntent6(PacketContext context,
    ConnectPoint egress, MacAddress srcMac, MacAddress dstMac) {
       IPv6 ipv6Pkt = (IPv6) context.inPacket().parsed().getPayload();
       Ip6Address dstIp = Ip6Address.valueOf(ipv6Pkt.getDestinationAddress());
       IpPrefix ip6Prefix = routeService.longestPrefixLookup(dstIp).get().prefix();

       TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                   .matchEthType(Ethernet.TYPE_IPV6)
                   .matchIPv6Dst(ip6Prefix);

       /* 要把 src MAC 跟 dst MAC 換掉 */
       TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder()
                   .setEthSrc(srcMac)
                   .setEthDst(dstMac);

       Set<FilteredConnectPoint> ingressPoints = new HashSet<FilteredConnectPoint>();
       FilteredConnectPoint egressPoint = new FilteredConnectPoint(egress);
       for (ConnectPoint cp : peerCPs) {
           if (!cp.equals(egress)) {
               ingressPoints.add(new FilteredConnectPoint(cp));
               log.info("[External_External] " + cp + " => " + egress + " is submitted.");
           }
       }

       MultiPointToSinglePointIntent intent = MultiPointToSinglePointIntent.builder()
                   .filteredIngressPoints(ingressPoints)
                   .filteredEgressPoint(egressPoint)
                   .selector(selector.build())
                   .treatment(treatment.build())
                   .priority(60)
                   .appId(appId)
                   .build();
       intentService.submit(intent);
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

    private void installRlueByRule(PacketContext context, PortNumber dstProt) {
        log.info("Install rule by rule");
        log.info("packetIn deviceID" + context.inPacket().receivedFrom().deviceId());
        log.info("packetIn srcMac" + context.inPacket().parsed().getSourceMAC());
        log.info("packetIn dstMac" + context.inPacket().parsed().getDestinationMAC());
        log.info("packetIn dstProt" + dstProt);
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
    private boolean isRelevantPrefix(IpAddress address) {
        // IPv4 prefixes to handle
        String[] ipv4Prefixes = {
            "172.16.11.0/24",
            "172.16.10.0/24",
            "172.16.12.0/24",
            "172.17.11.0/24",
            "192.168.70.0/24",
            "192.168.63.0/24",
            "192.168.65.0/24",
            "192.168.66.0/24",
            "192.168.100.0/24",
            "172.17.12.0/24",
            "172.17.10.0/24"
        };

        // Check if the address matches any of our prefixes
        for (String prefixStr : ipv4Prefixes) {
            IpPrefix prefix = IpPrefix.valueOf(prefixStr);
            if (prefix.contains(address)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRelevantPrefix6(Ip6Address address) {
        // IPv6 prefixes to handle
        String[] ipv6Prefixes = {
            "2a0b:4e07:c4:11::/64",    // Host 1's prefix
            "2a0b:4e07:c4:111::/64",  // Host 2's prefix
            "fd70::/64",              // IXP prefix
            "fd63::/64",               // FRRouting prefix
            "2a0b:4e07:c4:12::/64",
            "2a0b:4e07:c4:112::/64",
            "2a0b:4e07:c4:10::/64",
            "2a0b:4e07:c4:110::/64",

            "2400:6180::/48",
            "2400:6180:100::/40",
            "2604:a880:1::/48",
            "2604:a880:800::/48",
            "2a03:b0c0:1::/48",
            "2a03:b0c0:2::/48",
            "2a0b:4d07:101::/48"
        };

        // Check if the address matches any of our prefixes
        for (String prefixStr : ipv6Prefixes) {
            IpPrefix prefix = IpPrefix.valueOf(prefixStr);
            if (prefix.contains(address)) {
                return true;
            }
        }
        return false;
    }
}