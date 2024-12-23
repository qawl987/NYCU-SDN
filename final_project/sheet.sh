# show bridge information
sudo ovs-vsctl show

# show netns 
ip netns list

# ssh to onos
ssh -o "StrictHostKeyChecking=no" \
    -o GlobalKnownHostsFile=/dev/null \
    -o UserKnownHostsFile=/dev/null \
    onos@localhost -p 8101