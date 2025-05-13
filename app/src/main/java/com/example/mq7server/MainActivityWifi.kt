// MainActivityWifi.kt con importaciones corregidas
package com.example.mq7server

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
import com.example.mq7server.ui.theme.MQ7ServerTheme
import kotlinx.coroutines.launch

class MainActivityWifi : ComponentActivity() {
    private val TAG = "MainActivityWifi"
    private var viewModel: GasSensorWifiViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MQ7ServerTheme {
                val viewModelInstance: GasSensorWifiViewModel = viewModel()
                this.viewModel = viewModelInstance

                // URL del servidor - esto se puede hacer configurable
                val serverUrl = remember { mutableStateOf("https://lap-zic.tail792329.ts.net/") }

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

        // Área para mostrar datos del sensor
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isConnected) {
                // Aquí mostraremos los datos del sensor
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Datos del sensor",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Mostrar el tipo de gas actual
                    Text(
                        text = "Gas actual: ${currentGasType.name}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // Si hay datos de voltaje, mostrarlos
                    Text(
                        text = "Voltaje: ${viewModel.voltage.value} V",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    // Si hay datos de PPM, mostrarlos
                    Text(
                        text = "PPM: ${viewModel.ppmValue.value} ppm",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    // Si hay datos de ADC, mostrarlos
                    Text(
                        text = "ADC: ${viewModel.rawValue.value}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    // Selector de tipo de gas
                    GasTypeSelector(
                        currentGasType = currentGasType,
                        isChangingGas = isChangingGas,
                        onGasTypeSelected = { viewModel.changeGasType(it) },
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else {
                Text("Conéctate al servidor para ver los datos del sensor")
            }
        }
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

@Composable
fun GasTypeSelector(
    currentGasType: GasType,
    isChangingGas: Boolean,
    onGasTypeSelected: (GasType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Seleccionar tipo de gas:",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Botones para cada tipo de gas
            GasType.values().filter { it != GasType.UNKNOWN }.forEach { gasType ->
                Button(
                    onClick = { onGasTypeSelected(gasType) },
                    enabled = !isChangingGas && gasType != currentGasType,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (gasType == currentGasType)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(gasType.name)
                }
            }
        }

        // Indicador de cambio
        if (isChangingGas) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cambiando tipo de gas...")
            }
        }
    }
}