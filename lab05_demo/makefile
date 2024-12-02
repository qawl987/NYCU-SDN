# Define variables
BRIDGE1=ovs1
BRIDGE2=ovs2
GATEWAY1=172.18.0.1
GATEWAY2=172.19.0.1
CONTAINERS=R1 R3 R4 onos
CONTAINER_IPS1=\
    R1=172.18.0.2/24 \
    h1=172.18.0.3/24
CONTAINER_IPS2=\
    R2=172.19.0.2/24 \
    h2=172.19.0.3/24 \
    onos=172.19.0.4/24

# Default target
all: docker-build add-ovs-bridge configure-network
# all: add-ovs-bridge configure-network
docker-build:
	docker compose down -v
	docker compose build
	docker compose up -d

add-ovs-bridge:
	# sudo apt install openvswitch-switch
	sudo ovs-vsctl add-br $(BRIDGE1)
	sudo ovs-vsctl add-br $(BRIDGE2)
# Clean up disconnected ports
clean-ports:
	for port in $$(sudo ovs-vsctl list-ports $(BRIDGE1)); do \
		sudo ovs-vsctl del-port $(BRIDGE1) $$port; \
	done
	for port in $$(sudo ovs-vsctl list-ports $(BRIDGE2)); do \
		sudo ovs-vsctl del-port $(BRIDGE2) $$port; \
	done

# Add ports back to containers
configure-network: clean-ports
	@for entry in $(CONTAINER_IPS1); do \
		container=$$(echo $$entry | cut -d= -f1); \
		ipaddress=$$(echo $$entry | cut -d= -f2); \
		sudo ovs-docker add-port $(BRIDGE1) OVSbr $$container --ipaddress=$$ipaddress --gateway=$(GATEWAY1); \
	done
	@for entry in $(CONTAINER_IPS2); do \
		container=$$(echo $$entry | cut -d= -f1); \
		ipaddress=$$(echo $$entry | cut -d= -f2); \
		sudo ovs-docker add-port $(BRIDGE2) OVSbr $$container --ipaddress=$$ipaddress --gateway=$(GATEWAY2); \
	done
	sudo ovs-vsctl set-controller $(BRIDGE1) tcp:172.17.0.1:6653
	sudo ovs-vsctl set bridge $(BRIDGE1) protocols=OpenFlow14
	sudo ip addr add 172.18.0.1/24 dev $(BRIDGE1)
	sudo ovs-vsctl set-controller $(BRIDGE2) tcp:172.17.0.1:6653
	sudo ovs-vsctl set bridge $(BRIDGE2) protocols=OpenFlow14
	sudo ip addr add 172.19.0.1/24 dev $(BRIDGE2)
