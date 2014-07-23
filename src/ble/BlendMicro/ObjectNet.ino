/*


*/

#include <SPI.h>
#include <boards.h>
#include "RBL_nRF8001.h"
#include "Boards.h"

#define R_PIN 9
#define G_PIN 10
#define B_PIN 11

void setup() {

  Serial.begin(57600);
  
  for(int pin = 0; pin < TOTAL_PINS; pin++) {
    pinMode(pin, INPUT_PULLUP);
    digitalWrite(pin, HIGH);
  }

  pinMode(R_PIN, OUTPUT);
  pinMode(G_PIN, OUTPUT);
  pinMode(B_PIN, OUTPUT);
  analogWrite(R_PIN, 0x00);
  analogWrite(G_PIN, 0x00);
  analogWrite(B_PIN, 0xff);

  ble_set_name("RGB Light");

  ble_begin();
}

static byte buf_len = 0;

#define ADV_URL_CMD 'U'
#define RGB_VAL_CMD 'R'

void loop() {

  while(ble_available()) {

    byte cmd;
    cmd = ble_read();
    Serial.write(cmd);
    Serial.println("<");
    
    switch (cmd) {

      case ADV_URL_CMD: {
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
        
      case RGB_VAL_CMD: {
          byte r=ble_read();
          byte g=ble_read();
          byte b=ble_read();
          analogWrite(R_PIN, r);
          analogWrite(G_PIN, g);
          analogWrite(B_PIN, b);
          Serial.println(r); Serial.println(g); Serial.println(b);
      }
      break;
    }
  }

  ble_do_events();
  buf_len = 0;
}

