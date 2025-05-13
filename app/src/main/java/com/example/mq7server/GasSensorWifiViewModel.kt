// GasSensorWifiViewModel.kt
package com.example.mq7server

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

/**
 * ViewModel adaptado para usar WebSocket en lugar de BLE
 * Esta versión mantiene la misma API para que la UI no necesite cambios
 */
class GasSensorWifiViewModel : ViewModel() {
    private val TAG = "GasSensorWifiViewModel"

    // Estados para la UI
    private val _connectionStatus = mutableStateOf("Desconectado")
    val connectionStatus: State<String> = _connectionStatus

    private val _isConnected = mutableStateOf(false)
    val isConnected: State<Boolean> = _isConnected

    // Configuración del servidor
    private var serverUrl = "ws://192.168.1.100:3000" // URL por defecto, debe ser configurable
    private var webSocketClient: WebSocketClient? = null

    // Datos del sensor
    private val _rawValue = mutableStateOf(0)
    val rawValue: State<Int> = _rawValue

    private val _voltage = mutableStateOf(0.0)
    val voltage: State<Double> = _voltage

    private val _ppmValue = mutableStateOf(0.0)
    val ppmValue: State<Double> = _ppmValue

    // Tipo de gas actual
    private val _currentGasType = mutableStateOf(GasType.CO)
    val currentGasType: State<GasType> = _currentGasType

    // Estado para mostrar el proceso de cambio de gas
    private val _isChangingGas = mutableStateOf(false)
    val isChangingGas: State<Boolean> = _isChangingGas

    // Mensaje de estado para mostrar información al usuario
    private val _statusMessage = mutableStateOf("")
    val statusMessage: State<String> = _statusMessage

    // Datos para los gráficos por tipo de gas
    val gasChartData = mutableMapOf(
        GasType.CO to mutableStateListOf<DataPoint>(),
        GasType.H2 to mutableStateListOf<DataPoint>(),
        GasType.LPG to mutableStateListOf<DataPoint>(),
        GasType.CH4 to mutableStateListOf<DataPoint>(),
        GasType.ALCOHOL to mutableStateListOf<DataPoint>()
    )

    // Datos para el gráfico de ADC (común para todos los gases)
    val adcChartData = mutableStateListOf<DataPoint>()

    // Último timestamp para cada gas
    private val lastGasTimestamps = mutableMapOf<GasType, Long>()
    private var lastUpdateTime = 0L

    // Gas objetivo que hemos solicitado
    private var targetGasType: GasType? = null
    private var changeGasRequestTime: Long = 0

    private val dataManager = SensorDataManager()

    // Mínimos y máximos para cada gas
    private val minPPMValues = mutableMapOf<GasType, Double>()
    private val maxPPMValues = mutableMapOf<GasType, Double>()

    // Valores mínimos y máximos para ADC
    private val _minADCValue = mutableStateOf(4095)
    val minADCValue: State<Int> = _minADCValue

    private val _maxADCValue = mutableStateOf(0)
    val maxADCValue: State<Int> = _maxADCValue

    init {
        // Inicializar valores mínimos y máximos para cada gas
        GasType.values().forEach { gasType ->
            if (gasType != GasType.UNKNOWN) {
                minPPMValues[gasType] = Double.MAX_VALUE
                maxPPMValues[gasType] = 0.0
                lastGasTimestamps[gasType] = 0L

                // Inicializar gráficos con puntos vacíos
                if (gasChartData[gasType] == null) {
                    gasChartData[gasType] = mutableStateListOf()
                }

                // Añadir algunos puntos iniciales
                val initialPoints = gasChartData[gasType] ?: mutableStateListOf()
                if (initialPoints.isEmpty()) {
                    for (i in 0 until 5) {
                        initialPoints.add(DataPoint(i.toFloat(), 0.0f))
                    }
                }
            }
        }

        // Inicializar gráfico ADC con datos iniciales
        if (adcChartData.isEmpty()) {
            for (i in 0 until 5) {
                adcChartData.add(DataPoint(i.toFloat(), 0.0f))
            }
        }
    }

    // Configurar URL del servidor
    fun setServerUrl(url: String) {
        serverUrl = url
        Log.d(TAG, "URL del servidor configurada: $serverUrl")
    }

    // Inicializar WebSocketClient
    fun initWebSocketClient() {
        if (webSocketClient != null) {
            webSocketClient?.disconnect()
        }

        webSocketClient = WebSocketClient(serverUrl).apply {
            setCallbacks(object : WebSocketClient.Callbacks {
                override fun onConnected() {
                    viewModelScope.launch(Dispatchers.Main) {
                        Log.d(TAG, "WebSocket conectado")
                        _isConnected.value = true
                        _connectionStatus.value = "Conectado"
                        _statusMessage.value = "Conectado. Solicitando datos del sensor..."
                    }
                }

                override fun onDisconnected() {
                    viewModelScope.launch(Dispatchers.Main) {
                        Log.d(TAG, "WebSocket desconectado")
                        _isConnected.value = false
                        _connectionStatus.value = "Desconectado"
                        _statusMessage.value = "Desconectado"

                        // Resetear estado de cambio de gas
                        _isChangingGas.value = false
                        targetGasType = null
                    }
                }

                override fun onDataReceived(data: JSONObject) {
                    try {
                        Log.d(TAG, "Datos recibidos: $data")
                        lastUpdateTime = System.currentTimeMillis()

                        // Procesar datos del sensor
                        val rawValue = data.getInt("ADC")
                        val voltage = data.getDouble("V")
                        val ppm = data.getDouble("ppm")

                        // Obtener tipo de gas
                        val gasString = if (data.has("gas")) data.getString("gas") else "CO"
                        val gasType = GasType.fromString(gasString)

                        // Verificar si recibimos datos del gas objetivo
                        if (targetGasType != null && gasType == targetGasType) {
                            Log.d(TAG, "¡Recibidos datos del gas objetivo ${gasType.name}!")

                            viewModelScope.launch(Dispatchers.Main) {
                                _currentGasType.value = gasType
                                targetGasType = null
                                _isChangingGas.value = false
                            }
                        }

                        // Actualizar datos en el modelo
                        dataManager.updateData(rawValue, voltage, ppm, gasType)

                        // Registrar timestamp para este gas
                        lastGasTimestamps[gasType] = System.currentTimeMillis()

                        // Actualizar UI en el hilo principal
                        viewModelScope.launch(Dispatchers.Main) {
                            _rawValue.value = rawValue
                            _voltage.value = voltage
                            _ppmValue.value = ppm

                            // IMPORTANTE: Solo actualizar el gas actual si no estamos en proceso de cambio
                            if (!_isChangingGas.value) {
                                _currentGasType.value = gasType
                            }

                            // Actualizar los valores mínimos y máximos de ADC
                            _minADCValue.value = minOf(_minADCValue.value, rawValue)
                            _maxADCValue.value = maxOf(_maxADCValue.value, rawValue)

                            // Actualizar min/max para el gas actual
                            if (minPPMValues.containsKey(gasType)) {
                                minPPMValues[gasType] = minOf(minPPMValues[gasType] ?: Double.MAX_VALUE, ppm)
                                maxPPMValues[gasType] = maxOf(maxPPMValues[gasType] ?: 0.0, ppm)
                            }

                            // Actualizar datos del gráfico para el gas específico
                            val gasDataPoints = gasChartData[gasType] ?: run {
                                val newList = mutableStateListOf<DataPoint>()
                                gasChartData[gasType] = newList
                                newList
                            }

                            if (gasDataPoints.size >= 60) {
                                gasDataPoints.removeAt(0)
                            }
                            gasDataPoints.add(DataPoint(gasDataPoints.size.toFloat(), ppm.toFloat()))

                            // Actualizar datos del gráfico para ADC (común)
                            if (adcChartData.size >= 60) {
                                adcChartData.removeAt(0)
                            }
                            adcChartData.add(DataPoint(adcChartData.size.toFloat(), rawValue.toFloat()))
                        }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Error al procesar datos: ${e.message}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error general: ${e.message}")
                    }
                }

                override fun onGasChanged(gas: String, success: Boolean) {
                    viewModelScope.launch(Dispatchers.Main) {
                        if (success) {
                            val gasType = GasType.fromString(gas)
                            _currentGasType.value = gasType
                            targetGasType = null
                            _isChangingGas.value = false
                            _statusMessage.value = "Cambiado a gas ${gasType.name}"
                        } else {
                            _isChangingGas.value = false
                            targetGasType = null
                            _statusMessage.value = "Error al cambiar el tipo de gas"
                        }
                    }
                }

                override fun onStatusUpdate(status: JSONObject) {
                    try {
                        Log.d(TAG, "Actualización de estado recibida: $status")

                        // Si el mensaje incluye información sobre el gas actual
                        if (status.has("current_gas")) {
                            val currentGas = status.getString("current_gas")
                            val gasType = GasType.fromString(currentGas)

                            viewModelScope.launch(Dispatchers.Main) {
                                // Solo actualizar si no estamos cambiando de gas activamente
                                if (!_isChangingGas.value) {
                                    _currentGasType.value = gasType
                                    _statusMessage.value = "Estado: midiendo ${gasType.name}"
                                }
                            }
                        }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Error al procesar estado: ${e.message}")
                    }
                }

                override fun onError(message: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        Log.e(TAG, "Error: $message")
                        _statusMessage.value = "Error: $message"
                    }
                }
            })
        }
    }

    // Conectar al servidor WebSocket
    fun connect() {
        if (webSocketClient == null) {
            initWebSocketClient()
        }

        _connectionStatus.value = "Conectando..."
        _statusMessage.value = "Conectando al servidor..."

        webSocketClient?.connect()
    }

    // Desconectar del servidor
    fun disconnect() {
        Log.d(TAG, "Desconectando...")
        _statusMessage.value = "Desconectando..."
        webSocketClient?.disconnect()
    }

    // Función para obtener el rango de PPM para un tipo de gas
    fun getMinMaxPpmForGas(gasType: GasType): Pair<Double, Double> {
        val min = minPPMValues[gasType] ?: 0.0
        val max = maxPPMValues[gasType] ?: 100.0
        return if (min != Double.MAX_VALUE && max > min) {
            Pair(min, max)
        } else {
            Pair(0.0, 100.0) // Valores por defecto
        }
    }

    // Función para solicitar cambio de tipo de gas
    fun changeGasType(gasType: GasType) {
        if (!_isConnected.value) {
            Log.e(TAG, "No se puede cambiar el gas: dispositivo no conectado")
            _statusMessage.value = "Error: No conectado"
            return
        }

        if (_isChangingGas.value) {
            Log.e(TAG, "Ya se está cambiando el gas, espera un momento")
            _statusMessage.value = "Espera, ya se está cambiando el gas..."
            return
        }

        if (gasType == _currentGasType.value) {
            Log.d(TAG, "Ya estamos midiendo este gas: ${gasType.name}")
            _statusMessage.value = "Ya estamos midiendo ${gasType.name}"
            return
        }

        Log.d(TAG, "Solicitando cambio a gas: ${gasType.name}")
        _statusMessage.value = "Solicitando cambio a ${gasType.name}..."

        // Marcar que estamos en proceso de cambio
        _isChangingGas.value = true
        targetGasType = gasType
        changeGasRequestTime = System.currentTimeMillis()

        // Enviar comando al servidor
        webSocketClient?.changeGasType(gasType)

        // Timeout para resetear el estado de cambio
        viewModelScope.launch {
            delay(5000)
            if (_isChangingGas.value && targetGasType == gasType) {
                // Si todavía estamos esperando cambiar a este gas después de 5 segundos
                targetGasType = null
                _isChangingGas.value = false

                // Asumimos que el cambio se produjo pero no recibimos la confirmación
                _currentGasType.value = gasType
                _statusMessage.value = "Cambiado a ${gasType.name} (sin confirmación)"

                Log.w(TAG, "Timeout al cambiar el gas")

                // Solicitar estado para verificar
                webSocketClient?.requestSensorStatus()
            }
        }
    }

    // Determinar si un gas tiene datos
    fun hasGasData(gasType: GasType): Boolean {
        val dataPoints = gasChartData[gasType]
        return dataPoints != null && dataPoints.isNotEmpty() && dataPoints.any { it.y > 0 }
    }

    override fun onCleared() {
        super.onCleared()
        webSocketClient?.disconnect()
    }
}