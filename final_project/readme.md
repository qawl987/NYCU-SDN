### Test Command

- Build App

`mvn clean install -DskipTests`

`onos-app 192.168.100.1 deactivate proxyarp`

`onos-app 192.168.100.1 uninstall proxyarp`

`onos-app 192.168.100.1 install! target/ProxyArp-1.0-SNAPSHOT.oar`

- Check onos console output

Anywhere: `docker logs -f onos`

- Test arping

`docker exec -it h1 bin/sh` For enter h1, h2(can only use sh not bash)

`arping -I vethh1OVS2 172.16.11.69`

- change log level

In onos cli: `log:set ERROR org.onosproject`

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