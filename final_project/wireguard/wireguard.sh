sudo apt install -y wireguard
sudo cp my.conf /etc/wireguard/wg0.conf
sudo wg-quick up wg0
ping 192.168.61.254 -c 3 #gateway
ping 192.168.61.12 -c 3 #myID