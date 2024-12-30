stu_id=11
# h1 ping h2
echo "h1 ping h2"
sudo docker exec -it h1 ping6 2a0b:4e07:c4:1"$stu_id"::2 -c 3
# h2 ping h1
echo "h2 ping h1"
sudo docker exec -it h2 ping6 2a0b:4e07:c4:"$stu_id"::2 -c 3
# R1 ping h1
echo "R1 ping h1"
sudo docker exec -it R1 ping6 2a0b:4e07:c4:"$stu_id"::2 -c 3
# h1 ping R1
echo "h1 ping R1"
sudo docker exec -it h1 ping6 2a0b:4e07:c4:"$stu_id"::69 -c 3
# h2 ping R2
echo "h2 ping R2"
sudo docker exec -it h2 ping6 2a0b:4e07:c4:1"$stu_id"::1 -c 3
# R2 ping h2
echo "R2 ping h2"
sudo docker exec -it R2 ping6 2a0b:4e07:c4:1"$stu_id"::2 -c 3
# R1 ping R2
echo "R1 ping R2"
sudo docker exec -it R1 ping6 fd63::2 -c 3
# R2 ping R1
echo "R2 ping R1"
sudo docker exec -it R2 ping6 fd63::1 -c 3
# R1 ping IXP (AS65000)
echo "R1 ping TA_AS"
sudo docker exec -it R1 ping6 fd70::fe -c 3