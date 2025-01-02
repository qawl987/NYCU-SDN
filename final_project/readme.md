### Test Command

- Onos related

onos-cli: `ssh -o "StrictHostKeyChecking=no" -o GlobalKnownHostsFile=/dev/null -o UserKnownHostsFile=/dev/null onos@localhost -p 8101`

- Build App

`mvn clean install -DskipTests`

`onos-app 192.168.100.1 deactivate proxyarp`

`onos-app 192.168.100.1 uninstall proxyarp`

`onos-app 192.168.100.1 install! target/ProxyArp-1.0-SNAPSHOT.oar` 

- Check route # check route information(host unreachable)

`docker exec h1 ip route`see h1 how to route and default gateway (ip -6 route for ipv6)

`docker exec h1 ip neigh`  Display the ARP table of h1 ( ip -6 neigh for ipv6)

```nasm
// Example
192.168.1.1 dev eth0 lladdr 00:1a:2b:3c:4d:5e REACHABLE
192.168.1.2 dev eth0 lladdr 00:5e:4d:3c:2b:1a STALE
```

REACHABLE: Communication has recently occurred, so the system considers the entry valid.

STABLE: The neighbor information is still valid but hasn't been used recently.

FAILED: Attempts to communicate with the neighbor have failed.

`docker exec -it R1 vtysh`

`show ip route`show R1 route info

`show ip bgp` show bgp

`In onos-cli: routes` show routing info fpm push to onos

- Quick see ping packet

`sudo tcpdump -i any icmp`

- Check intent, use onos gui `http:/<host-ip>:8181/onos/ui`

Intent → top right ‘+’ button, can see selector, treatment and connect point

(connect point port often wrong, so use device page to see if the connect point is desired)

flow rule→ device can check intent have been install success or not

- Check onos console output

Anywhere: `docker logs -f onos` 

`docker logs -f onos | grep -v "LLDP Packet failed to validate!"`

- Check Mac for h1, R1

`docker exec R1 ip a`

- Upload config

`onos-netcfg 192.168.100.1 VrouterConfig.json`

- Test arping

`docker exec -it h1 bin/sh` For enter h1, h2(can only use sh not bash)

`arping -I vethh1OVS2 172.16.11.69`

- change log level

In onos cli: `log:set ERROR org.onosproject`

`log:set OFF nycu.winlab.bridge`

- Routing

`docker exec R1 ip route add 172.17.11.0/24 dev TOR2`

`docker exec h2 ip route add 172.16.11.0/24 dev vethh2R2`

`docker exec R2 ip route add 172.16.11.0/24 dev TOR1`

### Problem Faced

1. onos version:
    1. change `image: onosproject/onos:2.7.0` under docker-compose.yml

### Need Change

1. /config/R1/frr.conf
2. /config/R2/frr.conf
3. /vrouter/VrouterConfig.json
    1. ports
4. /bridge-app:
```jsx
arpTable.put(Ip4Address.valueOf("172.16.11.254"), MacAddress.valueOf("00:00:00:00:00:01"));
arpTable.put(Ip4Address.valueOf("192.168.63.1"), MacAddress.valueOf("1E:35:A3:75:B5:5A"));
arpTable.put(Ip4Address.valueOf("192.168.70.11"), MacAddress.valueOf("1E:35:A3:75:B5:5A"));
```

### Notes for viewers
1. Bridge in project do proxy arp quest, because we can't handle two packetprocessor handle same ipv4 packet problem, so we put bridge app function in vrouter app.
2. Add intent key in vrouter app, so you can use key to check exist or not, so you don't install intent rule every packet-in and then overwrite the last intent.(If the Intent rule establish slow due to path far, then flush intent would make ping fail)
3. Vxlan to teammate part is in the commit 867b1e9 may wrong, since it doesn't test during demo. Our private test sometime fail due to above reason(intent to teammate take time and next ping arrive so fresh the before intent rule).