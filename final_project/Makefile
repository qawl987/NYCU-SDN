CONTAINER=h1 h2 R1 R2
stu_id = 11

define netns
	sudo mkdir -p /var/run/netns
	for cname in $(CONTAINER); do \
	pid=$$(docker inspect -f '{{.State.Pid}}' $$cname); \
	sudo ln -sfT /proc/$$pid/ns/net /var/run/netns/$$cname; \
	done
endef

define create_as_veth
	$(eval $@_C1 = $(1))
	$(eval $@_C2 = $(2))
	$(eval $@_IP1= $(3))
	$(eval $@_IP2= $(4))
	$(eval $@_IP6_1= $(5))
	$(eval $@_IP6_2= $(6))
	$(eval $@_L1=veth${$@_C1}${$@_C2})
	$(eval $@_L2=veth${$@_C2}${$@_C1})
	sudo ip link add ${$@_L1} type veth peer name ${$@_L2}
	sudo ip link set ${$@_L1} netns ${$@_C1}
	sudo ip link set ${$@_L2} netns ${$@_C2}
	sudo ip netns exec ${$@_C1} ip link set ${$@_L1} up
	sudo ip netns exec ${$@_C2} ip link set ${$@_L2} up
	sudo ip netns exec ${$@_C1} ip addr add ${$@_IP1} dev ${$@_L1}
	sudo ip netns exec ${$@_C2} ip addr add ${$@_IP2} dev ${$@_L2}
	sudo ip netns exec ${$@_C1} ip -6 addr add ${$@_IP6_1} dev ${$@_L1}
	sudo ip netns exec ${$@_C2} ip -6 addr add ${$@_IP6_2} dev ${$@_L2}
endef

topo:
	docker compose up -d
	$(call netns)

	for cname in $(CONTAINER); do \
		docker exec -it $$cname sysctl -w net.ipv6.conf.all.disable_ipv6=0; \
		docker exec -it $$cname sysctl -w net.ipv6.conf.default.disable_ipv6=0; \
		docker exec -it $$cname sysctl -w net.ipv6.conf.lo.disable_ipv6=0; \
	done

	sudo ovs-vsctl add-br ovs1
	sudo ovs-vsctl add-br ovs2
	sudo ovs-vsctl set bridge ovs1 other_config:datapath-id=0000000000000001
	sudo ovs-vsctl set bridge ovs2 other_config:datapath-id=0000000000000002
	sudo ovs-vsctl set bridge ovs1 protocol=OpenFlow14
	sudo ovs-vsctl set bridge ovs2 protocol=OpenFlow14
	sudo ovs-vsctl set-controller ovs1 tcp:192.168.100.1:6653
	sudo ovs-vsctl set-controller ovs2 tcp:192.168.100.1:6653
   

	# sudo ip netns exec R1 route add default gw 192.168.100.1

	# vethovs1R1_63 vethovs1R1_70 vethovs1R1_16 vethovs1R1_100

	# link ovs1 and R1 with 192.168.100.3
	sudo ip link add vethovs1R1_100 type veth peer name vethR1onos
	sudo ip link set vethovs1R1_100 up
	sudo ip link set vethR1onos up
	sudo ovs-vsctl add-port ovs1 vethovs1R1_100
	sudo ip link set vethR1onos netns R1
	sudo ip netns exec R1 ip addr add 192.168.100.3/24 dev vethR1onos
	sudo ip netns exec R1 ip link set vethR1onos up
	sudo ip netns exec R1 route add default gw 192.168.100.1

	# link ovs1 and R1 with 192.168.63.1
	sudo ip link add vethovs1R1_63 type veth peer name vethR1R2
	sudo ip link set vethovs1R1_63 up
	sudo ip link set vethR1R2 up
	sudo ovs-vsctl add-port ovs1 vethovs1R1_63
	sudo ip link set vethR1R2 netns R1
	sudo ip netns exec R1 ip addr add 192.168.63.1/24 dev vethR1R2
	sudo ip netns exec R1 ip link set vethR1R2 up
	docker exec -it R1 ip -6 addr add fd63::1/64 dev vethR1R2

	# link ovs1 and R1 with 192.168.70.XX
	sudo ip link add vethovs1R1_70 type veth peer name vethR1TA
	sudo ip link set vethovs1R1_70 up
	sudo ip link set vethR1TA up
	sudo ovs-vsctl add-port ovs1 vethovs1R1_70
	sudo ip link set vethR1TA netns R1
	sudo ip netns exec R1 ip addr add 192.168.70.$(stu_id)/24 dev vethR1TA
	sudo ip netns exec R1 ip link set vethR1TA up
	docker exec -it R1 ip -6 addr add fd70::$(stu_id)/64 dev vethR1TA

	# link ovs1 and R1 with 172.16.XX.69
	sudo ip link add vethovs1R1_16 type veth peer name vethR1h1
	sudo ip link set vethovs1R1_16 up
	sudo ip link set vethR1h1 up
	sudo ovs-vsctl add-port ovs1 vethovs1R1_16
	sudo ip link set vethR1h1 netns R1
	sudo ip netns exec R1 ip addr add 172.16.$(stu_id).69/24 dev vethR1h1
	sudo ip netns exec R1 ip link set vethR1h1 up
	docker exec -it R1 ip -6 addr add 2a0b:4e07:c4:$(stu_id)::69/64 dev vethR1h1

	# link ovs1 and R2 with 192.168.63.XX
	sudo ip link add vethovs1R2 type veth peer name vethR2ovs1
	sudo ip link set vethovs1R2 up
	sudo ip link set vethR2ovs1 up
	sudo ovs-vsctl add-port ovs1 vethovs1R2
	sudo ip link set vethR2ovs1 netns R2
	sudo ip netns exec R2 ip addr add 192.168.63.2/24 dev vethR2ovs1
	docker exec -it R2 ip -6 addr add fd63::2/64 dev vethR2ovs1
	sudo ip netns exec R2 ip link set vethR2ovs1 up

	# link ovs2 and h1 with 172.16.XX.2
	sudo ip link add vethovs2h1 type veth peer name vethh1ovs2
	sudo ip link set vethovs2h1 up
	sudo ip link set vethh1ovs2 up
	sudo ovs-vsctl add-port ovs2 vethovs2h1
	sudo ip link set vethh1ovs2 netns h1
	sudo ip netns exec h1 ip addr add 172.16.$(stu_id).2/24 dev vethh1ovs2
	sudo ip netns exec h1 ip link set vethh1ovs2 up
	docker exec -it h1 ip -6 addr add 2a0b:4e07:c4:$(stu_id)::2/64 dev vethh1ovs2


	sudo ovs-vsctl add-port ovs2 TO_TA_VXLAN -- set interface TO_TA_VXLAN type=vxlan options:remote_ip=192.168.60.$(stu_id) options:local_ip=192.168.61.$(stu_id)

	# veth 192.168.100.1 for onos and ovs2 
	sudo ip link add veth0 type veth peer name veth1
	sudo ovs-vsctl add-port ovs2 veth0
	sudo ip link set veth0 up
	sudo ip link set veth1 up
	sudo ip a add 192.168.100.1/24 dev veth1

	# link ovs1 and ovs2
	sudo ip link add vethOVS1OVS2 type veth peer name vethOVS2OVS1
	sudo ovs-vsctl add-port ovs1 vethOVS1OVS2
	sudo ovs-vsctl add-port ovs2 vethOVS2OVS1
	sudo ip link set vethOVS1OVS2 up
	sudo ip link set vethOVS2OVS1 up


	# AS65XX1
	@$(call create_as_veth, R2, h2, 172.17.$(stu_id).1/24, 172.17.$(stu_id).2/24, 2a0b:4e07:c4:1$(stu_id)::1/64, 2a0b:4e07:c4:1$(stu_id)::2/64)
	docker exec -it h2 ip r add default via 172.17.$(stu_id).1
	docker exec -it h2 ip -6 r add default via 2a0b:4e07:c4:1$(stu_id)::1

	# Below for Peer vxlan transmit
	sudo ovs-vsctl add-port ovs2 TO_10 -- set interface TO_10 type=vxlan options:remote_ip=192.168.61.10 options:local_ip=192.168.61.$(stu_id)
	sudo ovs-vsctl add-port ovs2 TO_12 -- set interface TO_12 type=vxlan options:remote_ip=192.168.61.12 options:local_ip=192.168.61.$(stu_id)

	# link ovs1 and R1 with 192.168.64.XX
	sudo ip link add vethovs1R1_10 type veth peer name vethovs110
	sudo ip link set vethovs1R1_10 up
	sudo ip link set vethovs110 up
	sudo ovs-vsctl add-port ovs1 vethovs1R1_10
	sudo ip link set vethovs110 netns R1
	sudo ip netns exec R1 ip addr add 192.168.66.11/24 dev vethovs110
	sudo ip netns exec R1 ip link set vethovs110 up
    

	# link ovs1 and R1 with 192.168.66.XX
	sudo ip link add vethovs1R1_12 type veth peer name vethovs112
	sudo ip link set vethovs1R1_12 up
	sudo ip link set vethovs112 up
	sudo ovs-vsctl add-port ovs1 vethovs1R1_12
	sudo ip link set vethovs112 netns R1
	sudo ip netns exec R1 ip addr add 192.168.65.11/24 dev vethovs112
	sudo ip netns exec R1 ip link set vethovs112 up
ping:
	./ping.sh

ipv6:
	./ping_IPV6.sh

arping:
	./arping.sh

wireguard:
	cd wireguard 
	./wireguard.sh

re-build-bridge:
	@echo "re-build bridge-app"
	cd bridge-app && mvn clean install -DskipTests

re-build-vrouter:
	@echo "re-build vrouter"
	cd vrouter && mvn clean install -DskipTests

install-bridge:
	@echo "install bridge-app"
	onos-app 192.168.100.1 install! bridge-app/target/bridge-app-1.0-SNAPSHOT.oar

add-gateway:
	-sudo ip netns exec h1 route add default gw 172.16.$(stu_id).254
	-sudo ip netns exec h1 ip -6 route add default via 2a0b:4e07:c4:$(stu_id)::68

uninstall-bridge:
	@echo "uninstall bridge-app"
	onos-app 192.168.100.1 deactivate nycu.winlab.bridge
	onos-app 192.168.100.1 uninstall nycu.winlab.bridge

install-vrouter:
	@echo "install vrouter"
	onos-app 192.168.100.1 install! vrouter/target/vrouter-1.0-SNAPSHOT.oar
	
uninstall-vrouter:
	@echo "uninstall vrouter"
	onos-app 192.168.100.1 deactivate vrouter
	onos-app 192.168.100.1 uninstall vrouter

upload:
	@echo "upload"
	cd vrouter && onos-netcfg 192.168.100.1 VrouterConfig.json

clean: uninstall-vrouter uninstall-bridge

	sudo ovs-vsctl del-controller ovs1
	sudo ovs-vsctl del-controller ovs2
	sudo ovs-vsctl del-br ovs1
	sudo ovs-vsctl del-br ovs2
	docker compose down
	-sudo ip l del vethovs1R1_63 
	-sudo ip l del vethovs1R1_70 
	-sudo ip l del vethovs1R1_16 
	-sudo ip l del vethovs1R1_100 
	-sudo ip l del vethovs1R2 
	# sudo ip l del vethovs2h1 
	-sudo ip l del veth0 
	-sudo ip l del vethOVS1OVS2
	-sudo ip l del vethovs1R1_10
	-sudo ip l del vethovs1R1_12

	@basename -a /sys/class/net/veth* | grep -e '\([Rh][0-9]\)\{2\}' | xargs -I {} ip l del {} 2>/dev/null | true

show:
	sudo ovs-vsctl show
	ip netns list
	ls /sys/class/net/

set-log:
	sshpass -p rocks ssh -o "StrictHostKeyChecking=no" -o GlobalKnownHostsFile=/dev/null -o UserKnownHostsFile=/dev/null onos@localhost -p 8101  'log:set ERROR org.onosproject'