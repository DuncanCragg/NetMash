
import RPi.GPIO as GPIO
import time

def read_sensor():
    loops = 30
    hold = 0.05
    start = time.time()
    c=0
    for x in range(0,loops):
        GPIO.setup(4, GPIO.OUT)
        GPIO.output(4, GPIO.LOW)
        time.sleep(hold)
        GPIO.setup(4, GPIO.IN)
        while(GPIO.input(4)==GPIO.LOW):
            c+=1
    r = (int)(1000000 * ((time.time() - start) / loops - hold)) - 330
    return r

def to_moisture(us):
    m=(us-1000)/20
    if(m< 0):
        m=0
    if(m> 100):
        m=100
    return m

def writefile(list):
    outfile = open("/run/moisture.txt", "w")
    for n in list:
        outfile.write(str(n)+"\n")
    outfile.close()



GPIO.setmode(GPIO.BCM)
GPIO.setup(4, GPIO.OUT)

history = [ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 ]
i = 0

time.sleep(1)
while(1):
    us=read_sensor()
    moisture = to_moisture(us)
    history[i]=moisture
    print us, moisture, history
    i+=1
    if(i==len(history)):
        i=0
    writefile(history)


