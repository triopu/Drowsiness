#include <SoftwareSerial.h>

SoftwareSerial Bluetooth(2, 3); // RX | TX
long currentMillis, previousMillis;
//int data[]={700,750,740,800,720,710,754,753};
int data[]={1201,1003,1100,1022,1121,1224,1001,1002};
int i;

char outStr[5];
void setup(){
  Serial.begin(9600);
  Serial.println("Program Begin...");
  Bluetooth.begin(115200);  // HC-05 default speed in AT command more
  int i = 0;
}

void loop(){
  currentMillis = millis();
  int t = data[i];
  if(currentMillis - previousMillis >= t){
    // Mengirim data EKG melalui Bluetooth
    previousMillis = currentMillis;
    Serial.println(t);
    sprintf(outStr,"%04d",t);
    Bluetooth.print("*");
    Bluetooth.write(outStr);
    i = i+1;
    if(i>7) i = 0;
  }
} 
