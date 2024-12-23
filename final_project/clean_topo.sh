#!/bin/bash
if [ "$EUID" -ne 0 ]
  then echo "Please run as root"
  exit
fi

for c in h1 h2; do
    docker kill --signal=9 $c
    docker rm $c
done


basename -a /sys/class/net/* | grep veth | xargs -I '{}' ip l del {}
ovs-vsctl del-br OVS1
ovs-vsctl del-br OVS2
sudo ip -all netns delete
