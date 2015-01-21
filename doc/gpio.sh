#/bin/bash

gpio edge 7 rising

for i in 1 2 3 4 5
do
    echo "$i"
    gpio mode 7 output
    gpio write 7 0
    sleep 0.2
    gpio mode 7 tri
    gpio mode 7 input
    gpio mode 7 tri
    gpio wfi 7 rising
    echo done
done

