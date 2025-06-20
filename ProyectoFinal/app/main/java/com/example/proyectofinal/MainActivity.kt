package com.example.proyectofinal

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
    private val wifiHost = "192.168.1.42" // Asegúrate que esta sea la IP correcta de tu ESP32
    private val wifiPort = 80
    private var wifiSocket: Socket? = null
    private var wifiReader: BufferedReader? = null
    private var wifiConnected = false

    // --- Variables para Bluetooth ---
    private val bluetoothUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var bluetoothReader: BufferedReader? = null
    private var bluetoothConnected = false

    // --- Launchers para permisos y habilitar Bluetooth ---
    private val requestBluetoothPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allPermissionsGranted = true
        permissions.entries.forEach {
            if (!it.value) {
                allPermissionsGranted = false
            }
        }

        if (allPermissionsGranted) {
            Toast.makeText(this, "Todos los permisos fueron concedidos", Toast.LENGTH_SHORT).show()
            showBluetoothDevicesDialog()
        } else {
            Toast.makeText(this, "Permisos de Bluetooth denegados.", Toast.LENGTH_LONG).show()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, R.string.toast_bt_enabled, Toast.LENGTH_SHORT).show()
            checkBluetoothPermissionsAndConnect()
        } else {
            Toast.makeText(this, R.string.toast_bt_not_enabled, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar vistas
        tvStatus = findViewById(R.id.tv_status)
        tvTemperature = findViewById(R.id.tv_temperature)
        tvHumidity = findViewById(R.id.tv_humidity)
        tvAirQuality = findViewById(R.id.tv_air_quality)
        tvRain = findViewById(R.id.tv_rain)
        tvSoilMoisture = findViewById(R.id.tv_soil_moisture)
        btnConnectWifi = findViewById(R.id.btn_connect_wifi)
        btnConnectBluetooth = findViewById(R.id.btn_connect_bluetooth)

        // Inicializar BluetoothAdapter
        val bluetoothManager: BluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Configurar listeners de botones
        btnConnectWifi.setOnClickListener { connectToWifi() }
        btnConnectBluetooth.setOnClickListener { checkBluetoothPermissionsAndConnect() }

        // Estado inicial
        updateConnectionUI()
        resetSensorDisplay()
    }

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

        val requiredPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (requiredPermissions.isNotEmpty()) {
            requestBluetoothPermissionsLauncher.launch(requiredPermissions.toTypedArray())
        } else {
            showBluetoothDevicesDialog()
        }
    }

    private fun connectToWifi() {
        if (wifiConnected) {
            disconnectWifi()
            return
        }
        tvStatus.text = getString(R.string.status_connecting_wifi)
        thread {
            try {
                wifiSocket = Socket(wifiHost, wifiPort)
                wifiReader = BufferedReader(InputStreamReader(wifiSocket?.getInputStream()))
                wifiConnected = true
                runOnUiThread {
                    Toast.makeText(this, R.string.toast_connected_wifi, Toast.LENGTH_SHORT).show()
                    updateConnectionUI()
                }
                startReceivingWifiData()
            } catch (e: IOException) {
                Log.e("WIFI_CONNECT", "Error al conectar por Wi-Fi: ${e.message}")
                runOnUiThread {
                    wifiConnected = false
                    updateConnectionUI()
                    Toast.makeText(this, R.string.toast_error_connecting_wifi, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startReceivingWifiData() {
        thread {
            try {
                while (wifiConnected && wifiReader != null) {
                    val line = wifiReader?.readLine()
                    if (line != null) {
                        Log.d("WIFI_DATA", "Datos recibidos por Wi-Fi: $line")
                        parseAndDisplaySensorData(line)
                    } else { // El stream se cerró
                        break
                    }
                }
            } catch (e: IOException) {
                Log.e("WIFI_RECEIVE", "Error al recibir datos: ${e.message}")
            } finally {
                runOnUiThread { disconnectWifi() }
            }
        }
    }

    private fun disconnectWifi() {
        if (!wifiConnected) return
        wifiConnected = false
        try {
            wifiSocket?.close()
        } catch (e: IOException) {
            Log.e("WIFI_DISCONNECT", "Error al cerrar socket Wi-Fi: ${e.message}")
        }
        wifiSocket = null
        wifiReader = null
        runOnUiThread {
            updateConnectionUI()
            resetSensorDisplay()
        }
    }

    private fun showBluetoothDevicesDialog() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, R.string.toast_bt_not_available, Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        if (pairedDevices.isNullOrEmpty()) {
            Toast.makeText(this, R.string.toast_no_paired_devices, Toast.LENGTH_LONG).show()
            return
        }

        val deviceNames = pairedDevices.map { it.name ?: it.address }.toTypedArray()
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.dialog_select_bluetooth_device_title)
        builder.setItems(deviceNames) { _, which ->
            val selectedDevice = pairedDevices.toList()[which]
            connectToBluetoothDevice(selectedDevice)
        }
        builder.show()
    }

    private fun connectToBluetoothDevice(device: BluetoothDevice) {
        tvStatus.text = getString(R.string.status_connecting_bluetooth, device.name ?: device.address)
        thread {
            try {
                bluetoothDevice = device
                bluetoothSocket = device.createRfcommSocketToServiceRecord(bluetoothUuid)
                bluetoothSocket?.connect()
                bluetoothReader = BufferedReader(InputStreamReader(bluetoothSocket?.inputStream))
                bluetoothConnected = true
                runOnUiThread {
                    Toast.makeText(this, R.string.toast_connected_bluetooth, Toast.LENGTH_SHORT).show()
                    updateConnectionUI()
                }
                startReceivingBluetoothData()
            } catch (e: IOException) {
                Log.e("BLUETOOTH_CONNECT", "Error al conectar por Bluetooth: ${e.message}")
                runOnUiThread {
                    bluetoothConnected = false
                    updateConnectionUI()
                    Toast.makeText(this, getString(R.string.toast_error_connecting_bluetooth, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startReceivingBluetoothData() {
        thread {
            try {
                while (bluetoothConnected && bluetoothReader != null) {
                    val line = bluetoothReader?.readLine()
                    if (line != null) {
                        Log.d("BLUETOOTH_DATA", "Datos recibidos por Bluetooth: $line")
                        parseAndDisplaySensorData(line)
                    } else { // El stream se cerró
                        break
                    }
                }
            } catch (e: IOException) {
                Log.e("BLUETOOTH_RECEIVE", "Error al recibir datos: ${e.message}")
            } finally {
                runOnUiThread { disconnectBluetooth() }
            }
        }
    }

    private fun disconnectBluetooth() {
        if (!bluetoothConnected) return
        bluetoothConnected = false
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e("BLUETOOTH_DISCONNECT", "Error al cerrar socket Bluetooth: ${e.message}")
        }
        bluetoothSocket = null
        bluetoothReader = null
        bluetoothDevice = null
        runOnUiThread {
            updateConnectionUI()
            resetSensorDisplay()
        }
    }

    // === FUNCIÓN CORREGIDA ===
    private fun parseAndDisplaySensorData(data: String) {
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
                // Obtenemos el valor o un texto por defecto si no llega
                val tempValue = sensorMap["TEMP"] ?: getString(R.string.sensor_data_unavailable)
                val humValue = sensorMap["HUM"] ?: getString(R.string.sensor_data_unavailable)
                val airValue = sensorMap["AIR"] ?: getString(R.string.sensor_data_unavailable)
                val rainValue = sensorMap["RAIN"] ?: getString(R.string.sensor_data_unavailable)
                val soilValue = sensorMap["SOIL"] ?: getString(R.string.sensor_data_unavailable)

                // Construimos el texto final uniendo la etiqueta y el valor
                tvTemperature.text = "${getString(R.string.sensor_temperature_label)} $tempValue"
                tvHumidity.text = "${getString(R.string.sensor_humidity_label)} $humValue"
                tvAirQuality.text = "${getString(R.string.sensor_air_quality_label)} $airValue"
                tvRain.text = "${getString(R.string.sensor_rain_label)} $rainValue"
                tvSoilMoisture.text = "${getString(R.string.sensor_soil_moisture_label)} $soilValue"
            }
        } catch (e: Exception) {
            Log.e("PARSE_DATA", "Error al parsear datos: $data - ${e.message}")
            runOnUiThread { Toast.makeText(this, R.string.toast_error_parsing_data, Toast.LENGTH_SHORT).show() }
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

    // --- FUNCIÓN CENTRALIZADA PARA ACTUALIZAR LA UI ---
    @SuppressLint("SetTextI18n")
    private fun updateConnectionUI() {
        runOnUiThread {
            if (wifiConnected) {
                // Estado: Conectado por Wi-Fi
                btnConnectWifi.text = getString(R.string.disconnect_wifi_button_text)
                btnConnectBluetooth.isEnabled = false
                tvStatus.text = "${getString(R.string.status_connected_to)} $wifiHost"
            } else if (bluetoothConnected) {
                // Estado: Conectado por Bluetooth
                btnConnectBluetooth.text = getString(R.string.disconnect_bluetooth_button_text)
                btnConnectWifi.isEnabled = false
                val deviceIdentifier = bluetoothDevice?.name ?: bluetoothDevice?.address
                tvStatus.text = "${getString(R.string.status_connected_to)} ${deviceIdentifier ?: "dispositivo"}"
            } else {
                // Estado: Desconectado
                btnConnectWifi.text = getString(R.string.connect_wifi_button_text)
                btnConnectBluetooth.text = getString(R.string.connect_bluetooth_button_text)
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
