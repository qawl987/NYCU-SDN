# Define variables
BRIDGE=ovs-bridge
GATEWAY=172.20.0.1
CONTAINERS=R1 R3 R4 onos
CONTAINER_IPS=\
    R1=172.20.0.2/24 \
    R3=172.20.0.3/24 \
    R4=172.20.0.4/24 \
    onos=172.20.0.5/24

# Default target
all: docker-build add-ovs-bridge configure-network

docker-build:
	docker compose down -v
	docker compose build
	docker compose up -d

add-ovs-bridge:
	sudo apt install openvswitch-switch
	sudo ovs-vsctl add-br ovs-bridge

# Clean up disconnected ports
clean-ports:
	for port in $$(sudo ovs-vsctl list-ports $(BRIDGE)); do \
		sudo ovs-vsctl del-port $(BRIDGE) $$port; \
	done

# Add ports back to containers
configure-network: clean-ports
	@for entry in $(CONTAINER_IPS); do \
		container=$$(echo $$entry | cut -d= -f1); \
		ipaddress=$$(echo $$entry | cut -d= -f2); \
		sudo ovs-docker add-port $(BRIDGE) OVSbr $$container --ipaddress=$$ipaddress --gateway=$(GATEWAY); \
	done
	sudo ip addr add 172.20.0.1/24 dev ovs-bridge
