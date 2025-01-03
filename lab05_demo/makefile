# Define variables
BRIDGE1=ovs1
BRIDGE2=ovs2
GATEWAY1=172.18.0.1
GATEWAY2=172.19.0.1
CONTAINER=h1 h2 R1 R2

define netns
	sudo mkdir -p /var/run/netns
	for cname in $(CONTAINER); do \
	pid=$$(docker inspect -f '{{.State.Pid}}' $$cname); \
	sudo ln -sfT /proc/$$pid/ns/net /var/run/netns/$$cname; \
	done
endef

# Default target
all: docker-build add-ovs-bridge configure-network
# all: add-ovs-bridge configure-network
docker-build:
	docker compose down -v
	docker compose build
	docker compose up -d

add-ovs-bridge:
	sudo ovs-vsctl add-br $(BRIDGE1)
	sudo ovs-vsctl add-br $(BRIDGE2)
	sudo ovs-vsctl set bridge ovs1 protocol=OpenFlow14
	sudo ovs-vsctl set bridge ovs2 protocol=OpenFlow14
	sudo ovs-vsctl set-controller ovs1 tcp:172.17.0.1:6653
	sudo ovs-vsctl set-controller ovs2 tcp:172.17.0.1:6653

# Add ports back to containers
configure-network:

	$(call netns)

	# link ovs1 and R1 with 172.18.0.2
	sudo ip link add vethovs1R1 type veth peer name vethR1ovs1
	sudo ip link set vethovs1R1 up
	sudo ip link set vethR1ovs1 up
	sudo ovs-vsctl add-port ovs1 vethovs1R1

	sudo ip link set vethR1ovs1 netns R1
	sudo ip netns exec R1 ip addr add 172.18.0.2/24 dev vethR1ovs1
	sudo ip netns exec R1 ip link set vethR1ovs1 up

	# link ovs1 and h1 with 172.18.0.3
	sudo ip link add vethovs1h1 type veth peer name vethh1ovs1
	sudo ip link set vethovs1h1 up
	sudo ip link set vethh1ovs1 up
	sudo ovs-vsctl add-port ovs1 vethovs1h1

	sudo ip link set vethh1ovs1 netns h1
	sudo ip netns exec h1 ip addr add 172.18.0.3/24 dev vethh1ovs1
	sudo ip netns exec h1 ip link set vethh1ovs1 up

	# link ovs2 and R2 with 172.19.0.2
	sudo ip link add vethovs2R2 type veth peer name vethR2ovs2
	sudo ip link set vethovs2R2 up
	sudo ip link set vethR2ovs2 up
	sudo ovs-vsctl add-port ovs2 vethovs2R2

	sudo ip link set vethR2ovs2 netns R2
	sudo ip netns exec R2 ip addr add 172.19.0.2/24 dev vethR2ovs2
	sudo ip netns exec R2 ip link set vethR2ovs2 up

	# link ovs1 and h1 with 172.19.0.3
	sudo ip link add vethovs2h2 type veth peer name vethh2ovs2
	sudo ip link set vethovs2h2 up
	sudo ip link set vethh2ovs2 up
	sudo ovs-vsctl add-port ovs2 vethovs2h2

	sudo ip link set vethh2ovs2 netns h2
	sudo ip netns exec h2 ip addr add 172.19.0.3/24 dev vethh2ovs2
	sudo ip netns exec h2 ip link set vethh2ovs2 up

	# add gateway
	docker exec h1 ip route add default via 172.18.0.2
	docker exec h2 ip route add default via 172.19.0.2

	# add ovs1 and ovs2 to onos path
	sudo ip link add brovs1onos type bridge
	sudo ip addr add 172.17.0.2/24 dev brovs1onos
	sudo ip link add brovs2onos type bridge
	sudo ip addr add 172.17.0.3/24 dev brovs2onos
clean:
	sudo ovs-vsctl del-controller ovs1
	sudo ovs-vsctl del-controller ovs2
	sudo ovs-vsctl del-br ovs1
	sudo ovs-vsctl del-br ovs2
	docker compose down
	-sudo ip l del brovs1onos
	-sudo ip l del brovs2onos
show:
	sudo ovs-vsctl show
	ip netns list
	ls /sys/class/net/
