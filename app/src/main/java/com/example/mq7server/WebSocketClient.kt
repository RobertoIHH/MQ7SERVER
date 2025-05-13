// WebSocketClient.kt
package com.example.mq7server

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.*
import okio.ByteString

/**
 * Cliente WebSocket para comunicarse con el servidor central.
 * Reemplaza la funcionalidad BLE para conectarse a través de WiFi.
 */
class WebSocketClient(private val serverUrl: String) {
    private val TAG = "WebSocketClient"

    // Cliente OkHttp
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    // WebSocket
    private var webSocket: WebSocket? = null

    // Estado de conexión
    private var isConnected = AtomicBoolean(false)

    // Interfaz para notificar eventos al ViewModel
    interface Callbacks {
        fun onConnected()
        fun onDisconnected()
        fun onDataReceived(data: JSONObject)
        fun onGasChanged(gas: String, success: Boolean)
        fun onStatusUpdate(status: JSONObject)
        fun onError(message: String)
    }

    // Callbacks
    private var callbacks: Callbacks? = null

    // Configurar callbacks
    fun setCallbacks(callbacks: Callbacks) {
        this.callbacks = callbacks
    }

    // Conectar al servidor WebSocket
    fun connect() {
        if (isConnected.get()) {
            Log.d(TAG, "Ya conectado al servidor WebSocket")
            return
        }

        try {
            val request = Request.Builder()
                .url(serverUrl)
                .build()

            webSocket = client.newWebSocket(request, createWebSocketListener())

            Log.d(TAG, "Iniciando conexión WebSocket a $serverUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Error al conectar WebSocket: ${e.message}")
            callbacks?.onError("Error al conectar: ${e.message}")
        }
    }

    // Desconectar del servidor
    fun disconnect() {
        try {
            webSocket?.close(1000, "Cierre normal")
            webSocket = null
            isConnected.set(false)

            callbacks?.onDisconnected()
            Log.d(TAG, "WebSocket desconectado")
        } catch (e: Exception) {
            Log.e(TAG, "Error al desconectar WebSocket: ${e.message}")
        }
    }

    // Solicitar cambio de tipo de gas
    fun changeGasType(gasType: GasType) {
        if (!isConnected.get()) {
            Log.e(TAG, "No se puede cambiar gas: no conectado")
            callbacks?.onError("No conectado al servidor")
            return
        }

        try {
            val command = JSONObject().apply {
                put("command", "change_gas")
                put("gas", gasType.name)
                put("timestamp", System.currentTimeMillis())
            }

            webSocket?.send(command.toString())
            Log.d(TAG, "Enviado comando para cambiar a gas ${gasType.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar comando de cambio de gas: ${e.message}")
            callbacks?.onError("Error al enviar comando: ${e.message}")
        }
    }

    // Solicitar estado del sensor
    fun requestSensorStatus() {
        if (!isConnected.get()) {
            Log.e(TAG, "No se puede solicitar estado: no conectado")
            return
        }

        try {
            val command = JSONObject().apply {
                put("command", "get_status")
                put("timestamp", System.currentTimeMillis())
            }

            webSocket?.send(command.toString())
            Log.d(TAG, "Enviada solicitud de estado")
        } catch (e: Exception) {
            Log.e(TAG, "Error al solicitar estado: ${e.message}")
        }
    }

    // Crear listener para WebSocket
    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket conectado")
                isConnected.set(true)

                // Notificar al ViewModel
                callbacks?.onConnected()

                // Solicitar estado del sensor
                requestSensorStatus()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Mensaje recibido: $text")

                try {
                    val json = JSONObject(text)

                    // Procesar según el tipo de mensaje
                    when(json.optString("type")) {
                        "data_update" -> {
                            // Datos del sensor
                            if (json.has("data")) {
                                callbacks?.onDataReceived(json.getJSONObject("data"))
                            }
                        }
                        "gas_changed" -> {
                            // Confirmación de cambio de gas
                            val gas = json.optString("gas", "")
                            if (gas.isNotEmpty()) {
                                callbacks?.onGasChanged(gas, true)
                            }
                        }
                        "sensor_status" -> {
                            // Estado del sensor
                            if (json.has("status")) {
                                callbacks?.onStatusUpdate(json.getJSONObject("status"))
                            }
                        }
                        "error" -> {
                            // Error
                            callbacks?.onError(json.optString("message", "Error desconocido"))
                        }
                    }
                } catch (e: JSONException) {
                    Log.e(TAG, "Error al procesar mensaje JSON: ${e.message}")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "Mensaje binario recibido (no procesado)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Error en WebSocket: ${t.message}")
                isConnected.set(false)

                // Notificar al ViewModel
                callbacks?.onDisconnected()
                callbacks?.onError("Error de conexión: ${t.message}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket cerrándose, código: $code, razón: $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket cerrado, código: $code, razón: $reason")
                isConnected.set(false)

                // Notificar al ViewModel
                callbacks?.onDisconnected()
            }
        }
    }

    // Verificar si está conectado
    fun isConnected(): Boolean {
        return isConnected.get()
    }
}