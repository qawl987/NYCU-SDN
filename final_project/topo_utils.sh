#!/bin/bash
#set -x

if [ "$EUID" -ne 0 ]
  then echo "Please run as root"
  exit
fi

stu_id=11
# Creates a veth pair
# params: endpoint1 endpoint2
function create_veth_pair {
    ip link add $1 type veth peer name $2
    ip link set $1 up
    ip link set $2 up
}

# Add a container with a certain image
# params: image_name container_name
function add_container {
	docker run -dit --network=none --privileged --cap-add NET_ADMIN --cap-add SYS_MODULE \
		 --hostname $2 --name $2 ${@:3} $1
	pid=$(docker inspect -f '{{.State.Pid}}' $(docker ps -aqf "name=$2"))
	mkdir -p /var/run/netns
	ln -s /proc/$pid/ns/net /var/run/netns/$pid
}

# Set container interface's ip address and gateway
# params: container_name infname [ipaddress] [gw addr]
function set_intf_container {
    pid=$(docker inspect -f '{{.State.Pid}}' $(docker ps -aqf "name=$1"))
    ifname=$2
    ipaddr=$3
    echo "Add interface $ifname with ip $ipaddr to container $1"

    ip link set "$ifname" netns "$pid"
    if [ $# -ge 3 ]
    then
        ip netns exec "$pid" ip addr add "$ipaddr" dev "$ifname"
    fi
    ip netns exec "$pid" ip link set "$ifname" up
    if [ $# -ge 4 ]
    then
        ip netns exec "$pid" route add default gw $4
    fi
}

# Set container interface's ipv6 address and gateway
# params: container_name infname [ipaddress] [gw addr]
function set_v6intf_container {
    pid=$(docker inspect -f '{{.State.Pid}}' $(docker ps -aqf "name=$1"))
    ifname=$2
    ipaddr=$3
    echo "Add interface $ifname with ip $ipaddr to container $1"

    if [ $# -ge 3 ]
    then
        ip netns exec "$pid" ip addr add "$ipaddr" dev "$ifname"
    fi
    ip netns exec "$pid" ip link set "$ifname" up
    if [ $# -ge 4 ]
    then
        ip netns exec "$pid" route -6 add default gw $4
    fi
}

# Connects the bridge and the container
# params: bridge_name container_name [ipaddress] [gw addr]
function build_bridge_container_path {
    br_inf="veth$1$2"
    container_inf="veth$2$1"
    create_veth_pair $br_inf $container_inf
    brctl addif $1 $br_inf
    set_intf_container $2 $container_inf $3 $4
}

# Connects two ovsswitches
# params: OVS1 OVS2
function build_ovs_path {
    inf1="veth$1$2"
    inf2="veth$2$1"
    create_veth_pair $inf1 $inf2
    ovs-vsctl add-port $1 $inf1
    ovs-vsctl add-port $2 $inf2
}

# Connects a container to an ovsswitch
# params: ovs container [ipaddress] [gw addr]
function build_ovs_container_path {
    ovs_inf="veth$1$2"
    container_inf="veth$2$1"
    echo create_veth_pair $ovs_inf $container_inf
    create_veth_pair $ovs_inf $container_inf
    echo ovs-vsctl add-port $1 $ovs_inf
    ovs-vsctl add-port $1 $ovs_inf
    echo set_intf_container $2 $container_inf $3 $4
    set_intf_container $2 $container_inf $3 $4
}


HOSTIMAGE="sdnfv-final-host"
ROUTERIMAGE="sdnfv-final-frr"

# Build host base image
docker build containers/host -t "$HOSTIMAGE"
docker build containers/frr -t "$ROUTERIMAGE"

# TODO Write your own code
iptables -P FORWARD ACCEPT

# 建立 Host 1 容器
add_container $HOSTIMAGE h1
# # 建立 Host 2 容器
add_container $HOSTIMAGE h2

sudo docker compose up -d

sudo docker inspect -f '{{.State.Pid}}' $(docker ps -q -f "name=R1") | xargs -I {} ln -sfT /proc/{}/ns/net /var/run/netns/{}
sudo docker inspect -f '{{.State.Pid}}' $(docker ps -q -f "name=R2") | xargs -I {} ln -sfT /proc/{}/ns/net /var/run/netns/{}

# Create OVS bridges and configure ONOS controller
ovs-vsctl add-br OVS1 -- set bridge OVS1 protocols=OpenFlow13
ovs-vsctl add-br OVS2 -- set bridge OVS2 protocols=OpenFlow13
ovs-vsctl set-controller OVS1 tcp:192.168.100.1:6653
ovs-vsctl set-controller OVS2 tcp:192.168.100.1:6653

sudo ovs-vsctl add-port OVS2 TO_TA_VXLAN  -- set interface TO_TA_VXLAN type=vxlan options:remote_ip=192.168.60."$stu_id"
sudo ip link add vethOVS2OVS3 type veth peer name vethOVS3OVS2
sudo ovs-vsctl add-port OVS2 vethOVS2OVS3

sudo ip link set vethOVS2OVS3 up
sudo ip link set vethOVS3OVS2 up
sudo ip addr add 192.168.100.1/24 dev vethOVS3OVS2

# Connect OVS1 to OVS2 (via veth)
build_ovs_path OVS1 OVS2

build_ovs_container_path OVS2 h1 172.16."$stu_id".2/24 172.16."$stu_id".69


build_ovs_container_path OVS1 R2 192.168.63.2/24 192.168.63.1

build_ovs_container_path OVS1 R1 172.16."$stu_id".69/24 172.16."$stu_id".2

create_veth_pair vethR2R1 vethR1R2
ovs-vsctl add-port OVS1 vethR2R1
set_intf_container R1 vethR1R2 192.168.63.1/24 192.168.63.2

create_veth_pair vethONOSR1 vethR1ONOS
ovs-vsctl add-port OVS1 vethONOSR1
set_intf_container R1 vethR1ONOS 192.168.100.3/24 192.168.100.1

create_veth_pair vethTAR1 vethR1TA
ovs-vsctl add-port OVS1 vethTAR1
set_intf_container R1 vethR1TA 192.168.70."$stu_id"/24 192.168.70.253

create_veth_pair vethh2R2 vethR2h2
set_intf_container h2 vethh2R2 172.17."$stu_id".2/24 172.17."$stu_id".1
set_intf_container R2 vethR2h2 172.17."$stu_id".1/24 172.17."$stu_id".2

# params: container_name infname [ipaddress] [gw addr]
set_v6intf_container h1 vethh1OVS2 2a0b:4e07:c4:"$stu_id"::2/64 2a0b:4e07:c4:"$stu_id"::69

set_v6intf_container h2 vethh2R2 2a0b:4e07:c4:112::2/64 2a0b:4e07:c4:112::1
set_v6intf_container R2 vethR2h2 2a0b:4e07:c4:112::1/64 2a0b:4e07:c4:112::2

set_v6intf_container R2 vethR2OVS1 fd63::2/64 fd63::1

set_v6intf_container R1 vethR1OVS1 2a0b:4e07:c4:"$stu_id"::69/64 2a0b:4e07:c4:"$stu_id"::2
set_v6intf_container R1 vethR1R2 fd63::1/64 fd63::2
set_v6intf_container R1 vethR1TA fd70::"$stu_id"/64 fd70::fe









