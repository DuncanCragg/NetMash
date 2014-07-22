/*


*/

#include <SPI.h>
#include <boards.h>
#include "RBL_nRF8001.h"
#include "Boards.h"

#define R_PIN 1
#define G_PIN 1
#define B_PIN 1

void setup()
{
  Serial.begin(57600);
  Serial.println("BLE Arduino Slave");
  
  for(int pin = 0; pin < TOTAL_PINS; pin++) {
    pinMode(pin, INPUT);
    digitalWrite(pin, HIGH);
  }

  pinMode(PIN_TO_PWM(R_PIN), OUTPUT);
  pinMode(PIN_TO_PWM(G_PIN), OUTPUT);
  pinMode(PIN_TO_PWM(B_PIN), OUTPUT);

  ble_begin();
}

static byte buf_len = 0;

void ble_write_string(byte *bytes, uint8_t len)
{
  if (buf_len + len > 20)
  {
    for (int j = 0; j < 15000; j++) ble_do_events();
    buf_len = 0;
  }
  
  for (int j = 0; j < len; j++)
  {
    ble_write(bytes[j]);
    buf_len++;
  }
    
  if (buf_len == 20)
  {
    for (int j = 0; j < 15000; j++) ble_do_events();
    
    buf_len = 0;
  }  
}

#define ADV_URL_CMD 'U'
#define RGB_VAL_CMD 'R'

void loop()
{
  while(ble_available())
  {
    byte cmd;
    cmd = ble_read();
    Serial.write(cmd);
    Serial.println("<");
    
    switch (cmd)
    {
      case ADV_URL_CMD:
        {
          char ad[12];
          for(int i=0; i<12; i++){
              char c=ble_read();
              if(c== -1) c=0;
              ad[i]=c;
          }
          while(ble_read()!= -1);
          ble_set_advertising_data(ad,12);
        }
        break;
        
      case RGB_VAL_CMD:
        {
          byte r=ble_read();
          byte g=ble_read();
          byte b=ble_read();
          analogWrite(PIN_TO_PWM(R_PIN), r);
          analogWrite(PIN_TO_PWM(G_PIN), g);
          analogWrite(PIN_TO_PWM(B_PIN), b);
          Serial.println(r); Serial.println(g); Serial.println(b);
        }
        break;
    }

    ble_do_events();
    buf_len = 0;
    
    return;
  }

  if (Serial.available())
  {
    byte d = 'Z';
    ble_write(d);

    delay(5);
    while(Serial.available())
    {
      d = Serial.read();
      ble_write(d);
    }
    
    ble_do_events();
    buf_len = 0;
    
    return;    
  }

  ble_do_events();
  buf_len = 0;
}

