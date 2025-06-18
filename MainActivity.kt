package com.example.proyectofinal

import android.Manifest
import android.annotation.SuppressLint // Importar para @SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ProyectoFinal.R
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.UUID
import kotlin.concurrent.thread

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    // --- Declaración de vistas ---
    private lateinit var tvStatus: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var tvAirQuality: TextView
    private lateinit var tvRain: TextView
    private lateinit var tvSoilMoisture: TextView
    private lateinit var btnConnectWifi: Button
    private lateinit var btnConnectBluetooth: Button

    // --- Variables para Wi-Fi ---
    // ¡¡¡CAMBIA ESTO CON LA IP REAL DE TU ESP32 Y EL PUERTO!!!
    private val WIFI_HOST = "192.168.4.1" // Ejemplo: Si el ESP32 es un AP, suele ser 192.168.4.1. Si es STA, la IP de tu red.
    private val WIFI_PORT = 80            // Puerto que configuraste en el ESP32
    private var wifiSocket: Socket? = null
    private var wifiReader: BufferedReader? = null
    private var wifiWriter: PrintWriter? = null
    private var wifiConnected = false

    // --- Variables para Bluetooth ---
    // UUID estándar para Serial Port Profile (SPP), debe coincidir con el ESP32
    private val BLUETOOTH_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var bluetoothReader: BufferedReader? = null
    private var bluetoothConnected = false

    // --- Launchers para permisos y habilitar Bluetooth ---

    // Este launcher se usa para solicitar múltiples permisos, incluyendo ACCESS_FINE_LOCATION
    // Es compatible con Android 6.0 (API 23) y versiones posteriores
    private val requestBluetoothPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // En Android 6.0, ACCESS_FINE_LOCATION es el permiso de tiempo de ejecución clave para Bluetooth
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

        if (fineLocationGranted) {
            Toast.makeText(this, R.string.toast_bt_permissions_granted, Toast.LENGTH_SHORT).show()
            showBluetoothDevicesDialog()
        } else {
            Toast.makeText(this, R.string.toast_bt_permissions_denied, Toast.LENGTH_LONG).show()
        }
    }

    // Este launcher se usa para solicitar al usuario que habilite Bluetooth si está apagado
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, R.string.toast_bt_enabled, Toast.LENGTH_SHORT).show()
            // Bluetooth habilitado, ahora intenta conectar
            showBluetoothDevicesDialog()
        } else {
            Toast.makeText(this, R.string.toast_bt_not_enabled, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- Inicializar vistas ---
        tvStatus = findViewById(R.id.tv_status)
        tvTemperature = findViewById(R.id.tv_temperature)
        tvHumidity = findViewById(R.id.tv_humidity)
        tvAirQuality = findViewById(R.id.tv_air_quality)
        tvRain = findViewById(R.id.tv_rain)
        tvSoilMoisture = findViewById(R.id.tv_soil_moisture)
        btnConnectWifi = findViewById(R.id.btn_connect_wifi)
        btnConnectBluetooth = findViewById(R.id.btn_connect_bluetooth)

        // --- Inicializar BluetoothAdapter ---
        val bluetoothManager: BluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // --- Configurar listeners de botones ---
        btnConnectWifi.setOnClickListener {
            connectToWifi()
        }

        btnConnectBluetooth.setOnClickListener {
            checkBluetoothPermissionsAndConnect()
        }

        // Establecer el texto inicial de los botones y el estado
        updateConnectionUI()
        resetSensorDisplay()
    }

    // --- Funciones para Wi-Fi ---
    private fun connectToWifi() {
        if (wifiConnected) {
            disconnectWifi()
            return
        }

        tvStatus.text = getString(R.string.status_connecting_wifi)
        thread {
            try {
                // Abre el socket para la conexión Wi-Fi
                wifiSocket = Socket(WIFI_HOST, WIFI_PORT)
                wifiReader = BufferedReader(InputStreamReader(wifiSocket?.getInputStream()))
                wifiWriter = PrintWriter(wifiSocket!!.getOutputStream(), true) // auto-flush

                wifiConnected = true
                runOnUiThread {
                    tvStatus.text = getString(R.string.status_connected_wifi, WIFI_HOST, WIFI_PORT)
                    Toast.makeText(this, R.string.toast_connected_wifi, Toast.LENGTH_SHORT).show()
                    updateConnectionUI()
                }
                startReceivingWifiData()
            } catch (e: IOException) {
                Log.e("WIFI_CONNECT", "Error al conectar por Wi-Fi: ${e.message}")
                runOnUiThread {
                    tvStatus.text = getString(R.string.status_error_wifi)
                    Toast.makeText(this, R.string.toast_error_connecting_wifi, Toast.LENGTH_LONG).show()
                    wifiConnected = false
                    updateConnectionUI()
                }
                disconnectWifi() // Asegurarse de cerrar todo en caso de error
            }
        }
    }

    private fun startReceivingWifiData() {
        thread {
            var line: String?
            try {
                while (wifiConnected && wifiReader != null) {
                    line = wifiReader?.readLine() // Lee hasta el salto de línea
                    if (line != null) {
                        Log.d("WIFI_DATA", "Datos recibidos por Wi-Fi: $line")
                        parseAndDisplaySensorData(line)
                    }
                }
            } catch (e: IOException) {
                Log.e("WIFI_RECEIVE", "Error al recibir datos por Wi-Fi: ${e.message}")
                if (wifiConnected) {
                    runOnUiThread {
                        Toast.makeText(this, R.string.toast_wifi_connection_lost, Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                runOnUiThread {
                    disconnectWifi()
                }
            }
        }
    }

    private fun disconnectWifi() {
        wifiConnected = false
        try {
            wifiReader?.close()
            wifiWriter?.close()
            wifiSocket?.close()
        } catch (e: IOException) {
            Log.e("WIFI_DISCONNECT", "Error al cerrar conexión Wi-Fi: ${e.message}")
        } finally {
            wifiReader = null
            wifiWriter = null
            wifiSocket = null
            runOnUiThread {
                updateConnectionUI()
                resetSensorDisplay()
            }
        }
    }

    // --- Funciones para Bluetooth ---
    private fun checkBluetoothPermissionsAndConnect() {
        if (bluetoothConnected) {
            disconnectBluetooth()
            return
        }

        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.toast_no_bt_support, Toast.LENGTH_LONG).show()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
            return
        }

        // Permisos necesarios para Bluetooth en Android 6.0 (API 23)
        val requiredPermissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (requiredPermissions.isNotEmpty()) {
            requestBluetoothPermissionsLauncher.launch(requiredPermissions.toTypedArray())
        } else {
            showBluetoothDevicesDialog()
        }
    }

    // @SuppressLint("MissingPermission") es redundante aquí si ya está en la clase, pero se puede usar para granularidad
    private fun showBluetoothDevicesDialog() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, R.string.toast_bt_not_available, Toast.LENGTH_SHORT).show()
            return
        }

        // NO se necesita verificar BLUETOOTH_CONNECT aquí para API 23.
        // La anotación @SuppressLint("MissingPermission") en la clase principal ya lo maneja.
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices // Esta línea requiere BLUETOOTH_CONNECT en API 31+
        if (pairedDevices.isNullOrEmpty()) {
            Toast.makeText(this, R.string.toast_no_paired_devices, Toast.LENGTH_LONG).show()
            return
        }

        val deviceNames = pairedDevices.map { it.name ?: it.address }.toTypedArray()
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.dialog_select_bluetooth_device_title)
        builder.setItems(deviceNames) { dialog, which ->
            val selectedDevice = pairedDevices.toList()[which]
            connectToBluetoothDevice(selectedDevice)
        }
        builder.show()
    }

    // @SuppressLint("MissingPermission") es redundante aquí si ya está en la clase, pero se puede usar para granularidad
    private fun connectToBluetoothDevice(device: BluetoothDevice) {
        tvStatus.text = getString(R.string.status_connecting_bluetooth, device.name ?: device.address)
        bluetoothDevice = device

        thread {
            try {
                // NO se necesita verificar BLUETOOTH_CONNECT aquí para API 23.
                // La anotación @SuppressLint("MissingPermission") en la clase principal ya lo maneja.
                bluetoothSocket = device.createRfcommSocketToServiceRecord(BLUETOOTH_UUID)
                bluetoothSocket?.connect() // Esta línea requiere BLUETOOTH_CONNECT en API 31+

                bluetoothReader = BufferedReader(InputStreamReader(bluetoothSocket?.inputStream))

                bluetoothConnected = true
                runOnUiThread {
                    tvStatus.text = getString(R.string.status_connected_bluetooth, device.name ?: device.address)
                    Toast.makeText(this, R.string.toast_connected_bluetooth, Toast.LENGTH_SHORT).show()
                    updateConnectionUI()
                }
                startReceivingBluetoothData()
            } catch (e: IOException) {
                Log.e("BLUETOOTH_CONNECT", "Error al conectar por Bluetooth: ${e.message}")
                runOnUiThread {
                    tvStatus.text = getString(R.string.status_error_bluetooth)
                    Toast.makeText(this, getString(R.string.toast_error_connecting_bluetooth, e.toString()), Toast.LENGTH_LONG).show()
                    bluetoothConnected = false
                    updateConnectionUI()
                }
                disconnectBluetooth()
            }
        }
    }

    private fun startReceivingBluetoothData() {
        thread {
            var line: String?
            try {
                while (bluetoothConnected && bluetoothReader != null) {
                    line = bluetoothReader?.readLine() // Lee hasta el salto de línea
                    if (line != null) {
                        Log.d("BLUETOOTH_DATA", "Datos recibidos por Bluetooth: $line")
                        parseAndDisplaySensorData(line)
                    }
                }
            } catch (e: IOException) {
                Log.e("BLUETOOTH_RECEIVE", "Error al recibir datos por Bluetooth: ${e.message}")
                if (bluetoothConnected) {
                    runOnUiThread {
                        Toast.makeText(this, R.string.toast_bluetooth_connection_lost, Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                runOnUiThread {
                    disconnectBluetooth()
                }
            }
        }
    }

    private fun disconnectBluetooth() {
        bluetoothConnected = false
        try {
            bluetoothReader?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e("BLUETOOTH_DISCONNECT", "Error al cerrar conexión Bluetooth: ${e.message}")
        } finally {
            bluetoothReader = null
            bluetoothSocket = null
            bluetoothDevice = null
            runOnUiThread {
                updateConnectionUI()
                resetSensorDisplay()
            }
        }
    }

    // --- Funciones de Utilidad ---

    private fun parseAndDisplaySensorData(data: String) {
        // Asume que los datos vienen en un formato simple, por ejemplo:
        // "TEMP:25.5,HUM:60.2,AIR:300,RAIN:0,SOIL:500"
        // ¡Asegúrate de que tu ESP32 envíe los datos en este formato y con un salto de línea al final!
        try {
            val parts = data.split(",")
            val sensorMap = mutableMapOf<String, String>()
            for (part in parts) {
                val keyValue = part.split(":")
                if (keyValue.size == 2) {
                    sensorMap[keyValue[0].trim()] = keyValue[1].trim()
                }
            }

            runOnUiThread {
                tvTemperature.text = getString(R.string.sensor_temperature_value, sensorMap["TEMP"] ?: getString(R.string.sensor_data_unavailable))
                tvHumidity.text = getString(R.string.sensor_humidity_value, sensorMap["HUM"] ?: getString(R.string.sensor_data_unavailable))
                tvAirQuality.text = getString(R.string.sensor_air_quality_value, sensorMap["AIR"] ?: getString(R.string.sensor_data_unavailable))
                tvRain.text = getString(R.string.sensor_rain_value, sensorMap["RAIN"] ?: getString(R.string.sensor_data_unavailable))
                tvSoilMoisture.text = getString(R.string.sensor_soil_moisture_value, sensorMap["SOIL"] ?: getString(R.string.sensor_data_unavailable))
            }
        } catch (e: Exception) {
            Log.e("PARSE_DATA", "Error al parsear datos: $data - ${e.message}")
            runOnUiThread {
                Toast.makeText(this, R.string.toast_error_parsing_data, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetSensorDisplay() {
        runOnUiThread {
            tvTemperature.text = getString(R.string.sensor_temperature_label)
            tvHumidity.text = getString(R.string.sensor_humidity_label)
            tvAirQuality.text = getString(R.string.sensor_air_quality_label)
            tvRain.text = getString(R.string.sensor_rain_label)
            tvSoilMoisture.text = getString(R.string.sensor_soil_moisture_label)
        }
    }

    private fun updateConnectionUI() {
        runOnUiThread {
            if (wifiConnected) {
                btnConnectWifi.text = getString(R.string.disconnect_wifi_button_text)
                btnConnectBluetooth.isEnabled = false // Deshabilitar BT si Wi-Fi está conectado
            } else {
                btnConnectWifi.text = getString(R.string.connect_wifi_button_text)
                btnConnectBluetooth.isEnabled = true // Habilitar BT si Wi-Fi no está conectado
            }

            if (bluetoothConnected) {
                btnConnectBluetooth.text = getString(R.string.disconnect_bluetooth_button_text)
                btnConnectWifi.isEnabled = false // Deshabilitar Wi-Fi si BT está conectado
            } else {
                btnConnectBluetooth.text = getString(R.string.connect_bluetooth_button_text)
                btnConnectWifi.isEnabled = true // Habilitar Wi-Fi si BT no está conectado
            }

            // Si ambos están desconectados, asegura que ambos botones estén habilitados
            if (!wifiConnected && !bluetoothConnected) {
                btnConnectWifi.isEnabled = true
                btnConnectBluetooth.isEnabled = true
                tvStatus.text = getString(R.string.status_disconnected)
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        disconnectWifi()
        disconnectBluetooth()
    }
}