#include <LiquidCrystal.h>
#include <SoftwareSerial.h>
#include <SPI.h>
#include "SdFat.h"

#include <stdio.h>
#include <stdlib.h>

const uint8_t   chipSelect = 4;

#define FILE_BASE_NAME "THRS"
#define error(msg) sd.errorHalt(F(msg))

SdFat sd;
SdFile file;

SoftwareSerial Bluetooth(2,3); //RX, TX
LiquidCrystal lcd(10, 9, 8, 7, 6, 5);

#define AD8232      A0
#define LOPositive  0
#define LONegative  1
#define detButton   19

//Variabel waktu
unsigned long foundTimeMicros     = 0;
unsigned long old_foundTimeMicros = 0;
unsigned long currentMicros   = 0;
unsigned long previousMicros  = 0; 
const long    PERIOD             = 4000;

float recTime, thisTime, startTime;

float bpm = 0;
float beats;

char outStr[5];
float ecgData[3];
int active = 0;

void setup() {
  Serial.begin(19200);
  Bluetooth.begin(9600);
  lcd.begin(16, 2);
  pinMode(LOPositive, INPUT);
  pinMode(LONegative, INPUT);
  pinMode(AD8232, INPUT);
  pinMode(detButton, INPUT);

  const uint8_t BASE_NAME_SIZE = sizeof(FILE_BASE_NAME) - 1;
  char fileName[13] = FILE_BASE_NAME "00.txt";

  lcd.setCursor(0,0);
  lcd.print("Init SDCard");

  if(!sd.begin(chipSelect, SPI_FULL_SPEED)){
    sd.initErrorHalt();
    lcd.setCursor(0,1);
    lcd.print("Init Gagal");
  }
  else{
    lcd.setCursor(0,1);
    lcd.print("Init Selesai");
  }
  
  if (BASE_NAME_SIZE > 6) {
    error("FILE_BASE_NAME too long");
  }
  while (sd.exists(fileName)) {
    if (fileName[BASE_NAME_SIZE + 1] != '9') {
      fileName[BASE_NAME_SIZE + 1]++;
    } else if (fileName[BASE_NAME_SIZE] != '9') {
      fileName[BASE_NAME_SIZE + 1] = '0';
      fileName[BASE_NAME_SIZE]++;
    } else {
      error("Can't create file name");
    }
  }

  if(!file.open(fileName, O_CREAT | O_WRITE | O_EXCL)) {
    error("file.open");
  }
}

void loop() {
  BeatRate();
}

void BeatRate(){
  currentMicros = micros();
  
  
  // Beralih jika sudah waktunya untuk titik data baru (menurut PERIODE)
  if(currentMicros - previousMicros >= PERIOD){
    previousMicros = currentMicros;

    
    boolean QRS_detected = false;
    // Hanya membaca data dan melakukan deteksi jika lead hidup
    
 
    // Hanya membaca data jika EKG Chip telah mendeteksi bahwa lead menempel pada pasien
    // boolean leads_are_on = (digitalRead(LOPositive) == 0) && (digitalRead(LONegative) == 0);

    boolean leads_are_on = true;

    if(leads_are_on){
      
      // Membaca data titik EKG berikutnya
      int next_ecg_pt = analogRead(AD8232);
      Serial.println(next_ecg_pt);

      if(next_ecg_pt > 550){
        Serial.println(next_ecg_pt);
      }

      QRS_detected = detect(next_ecg_pt);

      if(QRS_detected == true){ 
        foundTimeMicros = micros();
        int bpm = (60.0/(((foundTimeMicros - old_foundTimeMicros))/1000000.0));
        beats = bpm;
        // Mengirim data EKG melalui Bluetooth
        sprintf(outStr,"%04d",next_ecg_pt);
        Bluetooth.print("*");
        Bluetooth.write(outStr);
        
        // Mengirim data BPM melalui Bluetooth
        sprintf(outStr,"%05d",bpm);
        Bluetooth.print("*");
        Bluetooth.write(outStr);
        
        old_foundTimeMicros = foundTimeMicros;
      }
      else{
        beats = 0.00;
        // Mengirim data EKG melalui Bluetooth
        sprintf(outStr,"%03d",next_ecg_pt);
        Bluetooth.print("*");
        Bluetooth.write(outStr);
      }

      if(digitalRead(detButton) == HIGH){
        lcd.clear();
        startTime = micros();
        active = active + 1;
        delay(500);
      }
      
      if(active > 2) active = 1;

      switch(active){
        case 1:
        lcd.setCursor(0,0);
        lcd.print("No Recording");
        break;

        case 2:
        lcd.setCursor(0,0);
        lcd.print("Recording...");
        recordData(next_ecg_pt, beats);
        break;
      }
    } 
  }
}

void recordData(float ecg_new_pt, float beat){

  thisTime = micros();

  float ecgVoltage = 3.226 * (ecg_new_pt/1100);
  float recTime = (thisTime - startTime)/1000000;

  String datalogString = String(recTime,4) + "\t" +  String(ecgVoltage,3) + "\t" + String(beat,1); 

  file.println(datalogString);

  if (!file.sync() || file.getWriteError()) {
    error("write error");
  }
}

boolean detect(float new_ecg_pt){
  ecgData[2] = ecgData[1];
  ecgData[1] = ecgData[0];
  ecgData[0] = new_ecg_pt;
  
  float point1 = ecgData[1] - ecgData[0];
  float point2 = ecgData[1] - ecgData[2]; 
  if(point1 > 0 && point2 > 0 && ecgData[1] > 530){
    return true;
  }
  return false;
}

