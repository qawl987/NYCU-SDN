stu_id=11
echo R1 arping h1
sudo docker exec -it R1 arping 172.16."$stu_id".2 -c 3

echo R2 arping h2
sudo docker exec -it R2 arping 172.17."$stu_id".2 -c 3

echo R1 arping R2
sudo docker exec -it R1 arping 192.168.63.2 -c 3

echo R2 arping R1
sudo docker exec -it R2 arping 192.168.63.1 -c 3

echo R1 arping onos
sudo docker exec -it R1 arping 192.168.100.1 -c 3

echo R1 arping TA_AS
sudo docker exec -it R1 arping 192.168.70.253 -c 3

