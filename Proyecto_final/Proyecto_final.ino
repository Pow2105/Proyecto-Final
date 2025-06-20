#include <WiFi.h>
#include <BluetoothSerial.h>
#include "DHT.h"

// Verificación de Bluetooth
#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error 
#endif

// OBJETOS GLOBALES
BluetoothSerial SerialBT; // Objeto para el Bluetooth Serial
WiFiServer server(80);    // Objeto para el servidor Wi-Fi en el puerto 80
WiFiClient client;        // Objeto para el cliente Wi-Fi

// CONFIGURACIÓN WI-FI
const char *ssid = "YENIS";
const char *password = "YEJA@25001";

// DEFINIMOS LOS PINES
#define DHTPIN 15
#define DHTTYPE DHT22
#define MQ135_PIN 35
#define RAIN_SENSOR_PIN 34
#define SOIL_MOISTURE_PIN 32

DHT dht(DHTPIN, DHTTYPE);

// === NUEVA FUNCIÓN DE CALLBACK PARA EVENTOS BLUETOOTH ===
void btCallback(esp_spp_cb_event_t event, esp_spp_cb_param_t *param) {
  if (event == ESP_SPP_SRV_OPEN_EVT) {
    Serial.println("¡Nuevo cliente Bluetooth conectado!");
  } else if (event == ESP_SPP_CLOSE_EVT) {
    Serial.println("Cliente Bluetooth desconectado.");
  }
}

void setup() {
  Serial.begin(115200);
  dht.begin();

  // --- INICIAR BLUETOOTH Y REGISTRAR EL CALLBACK ---
  SerialBT.register_callback(btCallback); // Registra nuestra función para eventos
  SerialBT.begin("Estacion_Sensores_ESP32");
  Serial.println("Servidor Bluetooth Clásico iniciado. Listo para emparejar.");

  // INICIAR WI-FI EN MODO ESTACIÓN
  Serial.print("Conectando a la red Wi-Fi: ");
  Serial.print(ssid);
  WiFi.begin(ssid, password);

  // VERIFICANDO QUE SE COMPLETE LA CONEXION
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\n¡Conectado a la red Wi-Fi!");

  // Imprimir la dirección IP asignada
  Serial.print("Dirección IP asignada: ");
  Serial.println(WiFi.localIP());

  // Iniciar el servidor TCP
  server.begin();
  Serial.println("Servidor Wi-Fi iniciado.");
}

void loop() {
  // LEE TODOS LOS SENSORES CONFIGURADOS
  float humedad = dht.readHumidity();
  float temperatura = dht.readTemperature();
  int airQualityValue = analogRead(MQ135_PIN);
  int rainValue = analogRead(RAIN_SENSOR_PIN);
  int soilMoistureValue = analogRead(SOIL_MOISTURE_PIN);

  if (isnan(humedad) || isnan(temperatura)) {
    temperatura = -1.0;
    humedad = -1.0;
  }

  int rainPercentage = map(rainValue, 4095, 0, 0, 100);
  int soilMoisturePercentage = map(soilMoistureValue, 4095, 1800, 0, 100);
  soilMoisturePercentage = constrain(soilMoisturePercentage, 0, 100);

  // Formato de datos con unidades: "TEMP:25.5 C,HUM:60.2 %,..."
  String dataString = "TEMP:" + String(temperatura, 1) + " C" +
                      ",HUM:" + String(humedad, 1) + " %" +
                      ",AIR:" + String(airQualityValue) +
                      ",RAIN:" + String(rainPercentage) + " %" +
                      ",SOIL:" + String(soilMoisturePercentage) + " %";

  // MANEJO DE LA CONEXION Y ENVIO DEL WIFI
  if (server.hasClient()) {
    if (client && client.connected()) {
      client.stop();
    }
    client = server.available();
    Serial.println("Nuevo cliente Wi-Fi conectado!");
  }

  if (client && client.connected()) {
    client.println(dataString);
  }

  // MANEJO DEL ENVIO BLUETOOTH
  if (SerialBT.hasClient()) {
    SerialBT.println(dataString);
  }

  // Imprimir en el monitor serial para depuración
  if (client && client.connected() || SerialBT.hasClient()){
    Serial.print("Enviando datos: ");
    Serial.println(dataString);
  }

  delay(5000);
}
