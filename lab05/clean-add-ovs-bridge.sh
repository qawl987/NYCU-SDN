#!/bin/bash

# Bridge name
BRIDGE="ovs-bridge"
GATEWAY="172.20.0.1"
# List of containers and IPs
declare -A containers
containers=(
  ["R1"]="172.20.0.2/24"
  ["R3"]="172.20.0.3/24"
  ["R4"]="172.20.0.4/24"
  ["onos"]="172.20.0.5/24"
)

# Clean up disconnected ports
for port in $(sudo ovs-vsctl list-ports $BRIDGE); do
  sudo ovs-vsctl del-port $BRIDGE $port
done

# Re-add ports
for container in "${!containers[@]}"; do
  sudo ovs-docker add-port $BRIDGE OVSbr $container --ipaddress=${containers[$container]} --gateway=$GATEWAY
done