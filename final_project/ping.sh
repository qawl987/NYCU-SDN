stu_id=11
echo R1 ping h1
sudo docker exec -it R1 ping 172.16."$stu_id".2 -c 3

echo h1 ping R1
sudo docker exec -it h1 ping 172.16."$stu_id".69 -c 3


echo h2 ping R2
sudo docker exec -it h2 ping 172.17."$stu_id".1 -c 3

echo R2 ping h2
sudo docker exec -it R2 ping 172.17."$stu_id".2 -c 3

echo R2 ping h2
sudo docker exec -it R2 ping 172.17."$stu_id".2 -c 3

echo R1 ping R2
sudo docker exec -it R1 ping 192.168.63.2 -c 3

echo R2 ping R1
sudo docker exec -it R2 ping 192.168.63.1 -c 3

echo R1 ping onos
sudo docker exec -it R1 ping 192.168.100.1 -c 3

echo R1 ping TA_AS
sudo docker exec -it R1 ping 192.168.70.253 -c 3

ping 192.168.100.2 -c 3