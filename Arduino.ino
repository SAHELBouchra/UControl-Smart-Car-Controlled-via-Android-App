#include <dht11.h>

#include <SoftwareSerial.h>
#define DHT11PIN 11  // DHT11 sensor connected to pin 4

// Motor control pins
int IN1 = 4; // Right forward
int IN2 = 5; // Left back
int IN3 = 6; // Left forward
int IN4 = 7; // Right back

// Bluetooth module connected to RX and TX pins
SoftwareSerial bluetooth(2, 3); // RX, TX pins for Bluetooth HC-06 (HC-06: RX->3, TX->2)

// Ultrasonic sensor pins
const int trigPin = 8;
const int echoPin = 9;

dht11 DHT11;

long duration;
int distance;

int motorSpeed = 150;  // Default speed
const int MIN_SPEED = 50;
const int MAX_SPEED = 255;

void setup() {
  // Configure motor pins
  pinMode(IN1, OUTPUT);
  pinMode(IN2, OUTPUT);
  pinMode(IN3, OUTPUT);
  pinMode(IN4, OUTPUT);

  // Configure ultrasonic sensor pins
  pinMode(trigPin, OUTPUT);
  pinMode(echoPin, INPUT);

  // Initialize serial communication
  Serial.begin(9600);
  bluetooth.begin(9600); // Set Bluetooth module baud rate
  
}

void loop() {
  static unsigned long lastDHTReadTime = 0;
  unsigned long currentMillis = millis();
  if (bluetooth.available()) {
    char command = bluetooth.read();
    Serial.print("Received command: ");
    Serial.println(command);
    executeCommand(command);
  }

  // Read DHT11 sensor every 5 seconds (5000 milliseconds)
  if (currentMillis - lastDHTReadTime >= 5000) {
    sendTemperatureHumidity();
    lastDHTReadTime = currentMillis;
  }

}

void executeCommand(char command) {
  switch (command) {
    case 'F': // Forward
      forward();
      delay(100);
      stop1();
      break;
    case 'B': // Backward
      backward();
      delay(100);
      stop1();
      break;
    case 'L': // Left
      left();
      delay(100);
      stop1();
      break;
    case 'R': // Right
      right();
      delay(100);
      stop1();
      break;
    case 'S': // Stop
      stop1();
      break;
    case 'A': // Auto mode
      AUTO();
      break;
    case 'I': // Increase Speed
      increaseSpeed();
      break;
    case 'D': // Decrease Speed
      decreaseSpeed();
      break;
    default:
      stop1();
  }
}

void forward() {
  digitalWrite(IN1, 0);
  digitalWrite(IN3, 0);
  analogWrite(IN2, motorSpeed);
  analogWrite(IN4, motorSpeed);
}

void backward() {
  analogWrite(IN1, motorSpeed);
  analogWrite(IN3, motorSpeed);
  digitalWrite(IN2, 0);
  digitalWrite(IN4, 0);
}

void right() {
  digitalWrite(IN1, 0);
  analogWrite(IN3, motorSpeed);
  analogWrite(IN2, motorSpeed);
  digitalWrite(IN4, 0);
}

void left() {
  analogWrite(IN1, motorSpeed);
  digitalWrite(IN3, 0);
  digitalWrite(IN2, 0);
  analogWrite(IN4, motorSpeed);
}

void stop1() {
  digitalWrite(IN1, 0);
  digitalWrite(IN3, 0);
  digitalWrite(IN2, 0);
  digitalWrite(IN4, 0);
}

void AUTO() {
  while (true) {
    if (bluetooth.available()) {
      char newCommand = bluetooth.read();
      executeCommand(newCommand);
      break;
    }

    // Ultrasonic sensor logic
    digitalWrite(trigPin, LOW);
    delayMicroseconds(2);
    digitalWrite(trigPin, HIGH);
    delayMicroseconds(10);
    digitalWrite(trigPin, LOW);

    duration = pulseIn(echoPin, HIGH);
    distance = duration * 0.034 / 2;

    Serial.print("Distance: ");
    Serial.println(distance);

    if (distance > 20) {
      forward();
      delay(100);
    } else if (distance <= 20 && distance > 10) {
      stop1();
      delay(500);
      left();
      delay(500);
    } else if (distance <= 10) {
      stop1();
      delay(500);
      backward();
      delay(500);
      right();
      delay(500);
    } else {
      stop1();
    }
  }
}

void decreaseSpeed() {
  motorSpeed -= 10;
  if (motorSpeed < MIN_SPEED) motorSpeed = MIN_SPEED;
  Serial.print("Speed decreased to: ");
  Serial.println(motorSpeed);
}

void increaseSpeed() {
  motorSpeed += 10;
  if (motorSpeed > MAX_SPEED) motorSpeed = MAX_SPEED;
  Serial.print("Speed increased to: ");
  Serial.println(motorSpeed);
}

void sendTemperatureHumidity() {
  int chk = DHT11.read(DHT11PIN);

  Serial.print("Humidity (%): ");
  Serial.println((float)DHT11.humidity, 2);

  Serial.print("Temperature (C): ");
  Serial.println((float)DHT11.temperature, 2);

  String data = "H:" + String(DHT11.humidity) + ",T:" + String(DHT11.temperature);
  bluetooth.println(data);
}
