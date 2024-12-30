main:
	mvn clean install -DskipTests

install:
	onos-app 192.168.100.1 install! target/bridge-app-1.0-SNAPSHOT.oar
uninstall:
	onos-app 192.168.100.1 deactivate nycu.winlab.bridge
	onos-app 192.168.100.1 uninstall nycu.winlab.bridge
