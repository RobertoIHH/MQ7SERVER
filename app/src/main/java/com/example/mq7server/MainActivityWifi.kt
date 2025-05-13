// MainActivityWifi.kt
package com.example.MQ7Monitor

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.MQ7Monitor.ui.theme.MQ7MonitorTheme
import kotlinx.coroutines.launch

class MainActivityWifi : ComponentActivity() {
    private val TAG = "MainActivityWifi"
    private var viewModel: GasSensorWifiViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MQ7MonitorTheme {
                val viewModelInstance: GasSensorWifiViewModel = viewModel()
                this.viewModel = viewModelInstance

                // URL del servidor - esto se puede hacer configurable
                val serverUrl = remember { mutableStateOf("ws://192.168.1.100:3000") }

                // Inicializar WebSocketClient
                LaunchedEffect(Unit) {
                    viewModelInstance.setServerUrl(serverUrl.value)
                    viewModelInstance.initWebSocketClient()
                    Log.d(TAG, "WebSocketClient inicializado")
                }

                WifiSensorGasApp(
                    viewModel = viewModelInstance,
                    serverUrl = serverUrl.value,
                    onServerUrlChange = {
                        serverUrl.value = it
                        viewModelInstance.setServerUrl(it)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel?.disconnect()
        Log.d(TAG, "Actividad destruida, conexión WebSocket cerrada")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiSensorGasApp(
    viewModel: GasSensorWifiViewModel,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit
) {
    val isConnected by viewModel.isConnected
    val connectionStatus by viewModel.connectionStatus
    val currentGasType by viewModel.currentGasType
    val isChangingGas by viewModel.isChangingGas
    val statusMessage by viewModel.statusMessage
    val scope = rememberCoroutineScope()

    var showServerDialog by remember { mutableStateOf(false) }
    var tempServerUrl by remember { mutableStateOf(serverUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Título
        Text(
            text = "Monitor Multisensor de Gases (WiFi)",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        // Información del servidor
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showServerDialog = true }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = "Servidor: $serverUrl",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Toca para cambiar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Botones de acción
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.connect() },
                modifier = Modifier.weight(1f),
                enabled = !isConnected
            ) {
                Text("Conectar")
            }

            Button(
                onClick = { viewModel.disconnect() },
                modifier = Modifier.weight(1f),
                enabled = isConnected
            ) {
                Text("Desconectar")
            }
        }

        // Estado de conexión y mensaje de estado
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Estado:",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = connectionStatus,
                color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            // Mensaje de estado dinámico
            if (statusMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Reutilizamos SensorGasApp pero con el nuevo ViewModel
        // Esto funciona porque mantuvimos la misma API en el ViewModel
        SensorGasApp(
            viewModel = viewModel,
            onScanClick = { /* No hacemos nada, no hay escaneo BLE */ }
        )
    }

    // Diálogo para cambiar la URL del servidor
    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("Configurar Servidor") },
            text = {
                Column {
                    Text("Introduce la URL del servidor WebSocket:")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = tempServerUrl,
                        onValueChange = { tempServerUrl = it },
                        placeholder = { Text("ws://dirección:puerto") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onServerUrlChange(tempServerUrl)
                        showServerDialog = false

                        // Si estábamos conectados, reconectar con la nueva URL
                        if (isConnected) {
                            scope.launch {
                                viewModel.disconnect()
                                viewModel.initWebSocketClient()
                                viewModel.connect()
                            }
                        }
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        tempServerUrl = serverUrl
                        showServerDialog = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}