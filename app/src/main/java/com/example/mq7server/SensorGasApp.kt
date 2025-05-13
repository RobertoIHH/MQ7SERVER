package com.example.MQ7Monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.util.Log
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// Colores para cada tipo de gas
val gasColors = mapOf(
    GasType.CO to Color(0xFF3366CC),       // Azul
    GasType.H2 to Color(0xFFDC3912),       // Rojo
    GasType.LPG to Color(0xFFFF9900),      // Naranja
    GasType.CH4 to Color(0xFF109618),      // Verde
    GasType.ALCOHOL to Color(0xFF990099),  // Morado
    GasType.UNKNOWN to Color.Gray         // Gris para gases desconocidos
)

@Composable
fun SensorGasApp(
    viewModel: GasSensorViewModel,
    onScanClick: () -> Unit
) {
    val isConnected by viewModel.isConnected
    val connectionStatus by viewModel.connectionStatus
    val isScanning by viewModel.isScanning
    val selectedDevice by viewModel.selectedDevice
    val currentGasType by viewModel.currentGasType
    val isChangingGas by viewModel.isChangingGas
    val statusMessage by viewModel.statusMessage

    // Importante: Usar DisposableEffect para efectos al montar y desmontar la composable
    DisposableEffect(Unit) {
        Log.d("SensorGasApp", "Componente SensorGasApp inicializado")
        onDispose {
            Log.d("SensorGasApp", "Componente SensorGasApp destruido")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Título
        Text(
            text = "Monitor Multisensor de Gases",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        // Botones de acción
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    Log.d("SensorGasApp", "Botón de escaneo presionado")
                    onScanClick() // Primero solicitar permisos si es necesario
                },
                modifier = Modifier.weight(1f),
                enabled = !isScanning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isScanning) "Escaneando..." else "Buscar",
                    color = Color.White
                )
            }

            Button(
                onClick = { viewModel.connectToDevice() },
                modifier = Modifier.weight(1f),
                enabled = selectedDevice != null && !isConnected
            ) {
                Text("Conectar")
            }

            Button(
                onClick = { viewModel.disconnectDevice() },
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
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = connectionStatus,
                color = if (isConnected) Color.Green else Color.Red
            )

            // Mensaje de estado dinámico
            if (statusMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = statusMessage,
                    color = Color.DarkGray,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        //lista Disp
        Text(
            text = "Dispositivos disponibles:",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )

        if (isScanning) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }

        // Imprimir log de dispositivos encontrados
        Log.d("SensorGasApp", "Renderizando ${viewModel.deviceList.size} dispositivos")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                .background(Color(0xFFF0F0F0))
        ) {
            if (viewModel.deviceList.isEmpty()) {
                // Mostrar mensaje si no hay dispositivos
                Text(
                    text = if (isScanning) "Buscando dispositivos..." else "No se encontraron dispositivos",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    color = Color.Gray
                )
            } else {
                // Mostrar la lista de dispositivos encontrados
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(viewModel.deviceList) { device ->
                        DeviceItem(
                            deviceInfo = device,
                            isSelected = selectedDevice?.address == device.address,
                            onClick = {
                                Log.d("SensorGasApp", "Dispositivo seleccionado: ${device.name}")
                                viewModel.selectDevice(device)
                            }
                        )
                    }
                }
            }
        }

        // Datos del sensor
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Mostrar el tipo de gas actual
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Gas actual: ",
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = gasColors[currentGasType] ?: Color.Gray,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = currentGasType.name,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    // Indicador de cambio de gas
                    if (isChangingGas) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Cambiando...",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Datos del sensor
                val rawValue by viewModel.rawValue
                val voltage by viewModel.voltage
                val ppmValue by viewModel.ppmValue

                Text(text = "ADC: $rawValue")
                Text(text = "Voltaje: ${String.format("%.2f", voltage)} V")
                Text(
                    text = "PPM: ${String.format("%.2f", ppmValue)} ppm",
                    fontWeight = FontWeight.Bold,
                    color = gasColors[currentGasType] ?: Color.Black
                )
            }
        }

        // Panel de selección de gases para cambiar el tipo de gas
        GasSelector(
            viewModel = viewModel,
            currentGasType = currentGasType,
            isConnected = isConnected,
            isChangingGas = isChangingGas,
            modifier = Modifier.fillMaxWidth()
        )

        // Gráfico en tiempo real
        Text(
            text = "Gráfico en tiempo real:",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                .padding(8.dp)
        ) {
            // Mostrar gráfico del gas actual
            val gasDataPoints = viewModel.gasChartData[currentGasType] ?: mutableStateListOf()

            if (gasDataPoints.isNotEmpty() || viewModel.adcChartData.isNotEmpty()) {
                MultiGasLineChart(
                    ppmDataPoints = gasDataPoints,
                    adcDataPoints = viewModel.adcChartData,
                    gasType = currentGasType,
                    viewModel = viewModel
                )
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Esperando datos de ${currentGasType.name}...",
                        color = Color.Gray
                    )

                    // Añadir mensaje de ayuda si estamos conectados pero no hay datos
                    if (isConnected && !isChangingGas) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Selecciona otro gas y luego regresa a este",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GasSelector(
    viewModel: GasSensorViewModel,
    currentGasType: GasType,
    isConnected: Boolean,
    isChangingGas: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier) {
        Text(
            text = "Seleccionar gas a medir:",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GasType.values().forEach { gasType ->
                if (gasType != GasType.UNKNOWN) {
                    val isSelected = gasType == currentGasType

                    GasButton(
                        gasType = gasType,
                        isSelected = isSelected,
                        enabled = isConnected && !isChangingGas,
                        onClick = {
                            Log.d("GasSelector", "Cambiando a gas: ${gasType.name}")
                            viewModel.changeGasType(gasType)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GasButton(
    gasType: GasType,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val buttonColor = if (isSelected) {
        gasColors[gasType] ?: Color.Gray
    } else {
        Color.LightGray.copy(alpha = 0.5f)
    }

    Button(
        onClick = onClick,
        enabled = enabled && !isSelected, // Deshabilitar el botón del gas ya seleccionado
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            disabledContainerColor = if (isSelected) buttonColor else Color.Gray.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.height(36.dp)
    ) {
        Text(
            text = gasType.name,
            color = if (isSelected) Color.White else Color.Black,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp
        )
    }
}

@Composable
fun DeviceItem(
    deviceInfo: DeviceInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) Color(0xFFE3F2FD) else Color.Transparent)
            .padding(12.dp), // Aumentado para mejor visualización
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = deviceInfo.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = deviceInfo.address,
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "RSSI: ${deviceInfo.rssi} dBm",
            fontSize = 12.sp,
            color = if (deviceInfo.rssi > -60) Color.Green else if (deviceInfo.rssi > -80) Color.Blue else Color.Red
        )
    }
}

@Composable
fun MultiGasLineChart(
    ppmDataPoints: List<DataPoint>,
    adcDataPoints: List<DataPoint>,
    gasType: GasType,
    viewModel: GasSensorViewModel
) {
    if (ppmDataPoints.isEmpty() && adcDataPoints.isEmpty()) return

    // Obtener color específico para el gas actual
    val gasColor = gasColors[gasType] ?: Color.Blue

    // Obtener los valores min/max para el gas actual
    val (minPPM, maxPPM) = viewModel.getMinMaxPpmForGas(gasType)

    // Acceder al ViewModel para obtener los valores min/max de ADC
    val minADC = viewModel.minADCValue.value.toFloat()
    val maxADC = viewModel.maxADCValue.value.toFloat().coerceAtLeast(minADC + 1f)

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val width = size.width
        val height = size.height
        val padding = 16.dp.toPx()
        val leftPadding = 40.dp.toPx() // Padding para etiquetas
        val rightPadding = 40.dp.toPx() // Padding adicional para la escala derecha

        // Calcular rango de PPM
        val ppmMin = minPPM.toFloat()
        val ppmMax = maxPPM.toFloat()
        val ppmRange = (ppmMax - ppmMin).coerceAtLeast(1f)

        // Usar los valores dinámicos para el eje ADC
        val adcRange = (maxADC - minADC).coerceAtLeast(1f)

        // Dibujar ejes
        drawLine(
            color = Color.Gray,
            start = Offset(leftPadding, padding),
            end = Offset(leftPadding, height - padding),
            strokeWidth = 1.dp.toPx()
        )

        drawLine(
            color = Color.Gray,
            start = Offset(leftPadding, height - padding),
            end = Offset(width - rightPadding, height - padding),
            strokeWidth = 1.dp.toPx()
        )

        // Dibujar eje Y secundario (derecho)
        drawLine(
            color = Color.Gray,
            start = Offset(width - rightPadding, padding),
            end = Offset(width - rightPadding, height - padding),
            strokeWidth = 1.dp.toPx()
        )

        // Dibujar grid horizontal
        val yLabelCount = 5
        for (i in 0..yLabelCount) {
            val yPos = height - padding - (i.toFloat() / yLabelCount) * (height - 2 * padding)

            // Línea horizontal de grid
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(leftPadding, yPos),
                end = Offset(width - rightPadding, yPos),
                strokeWidth = 0.5.dp.toPx()
            )
        }

        // Dibujar línea de datos PPM (específica para el gas)
        if (ppmDataPoints.size > 1) {
            val path = Path()
            val points = ppmDataPoints.mapIndexed { index, point ->
                // Escalar puntos al espacio del gráfico para PPM
                val x = leftPadding + (index.toFloat() / (ppmDataPoints.size - 1)) * (width - leftPadding - rightPadding)
                val normalizedValue = ((point.y - ppmMin) / ppmRange).coerceIn(0f, 1f)
                val y = height - padding - normalizedValue * (height - 2 * padding)
                Offset(x, y)
            }

            // Mover a primer punto
            path.moveTo(points.first().x, points.first().y)

            // Conectar resto de puntos
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }

            drawPath(
                path = path,
                color = gasColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Dibujar puntos
            points.forEach { point ->
                drawCircle(
                    color = gasColor,
                    radius = 3.dp.toPx(),
                    center = point
                )
            }
        }

        // Dibujar línea de datos ADC
        if (adcDataPoints.size > 1) {
            val path = Path()
            val points = adcDataPoints.mapIndexed { index, point ->
                // Escalar puntos al espacio del gráfico para ADC
                val x = leftPadding + (index.toFloat() / (adcDataPoints.size - 1)) * (width - leftPadding - rightPadding)
                // Usar el rango de ADC para calcular Y
                val normalizedValue = ((point.y - minADC) / adcRange).coerceIn(0f, 1f)
                val y = height - padding - normalizedValue * (height - 2 * padding)
                Offset(x, y)
            }

            // Mover a primer punto
            path.moveTo(points.first().x, points.first().y)

            // Conectar resto de puntos
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }

            drawPath(
                path = path,
                color = Color.Red,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Dibujar puntos
            points.forEach { point ->
                drawCircle(
                    color = Color.Red,
                    radius = 3.dp.toPx(),
                    center = point
                )
            }
        }

        // Dibujar etiquetas en el eje Y (izquierdo - PPM)
        for (i in 0..yLabelCount) {
            val yPos = height - padding - (i.toFloat() / yLabelCount) * (height - 2 * padding)
            val ppmValue = ppmMin + (i.toFloat() / yLabelCount) * ppmRange

            // Línea de marca
            drawLine(
                color = Color.LightGray,
                start = Offset(leftPadding - 5.dp.toPx(), yPos),
                end = Offset(leftPadding, yPos),
                strokeWidth = 1.dp.toPx()
            )

            // Texto del valor PPM
            drawContext.canvas.nativeCanvas.drawText(
                String.format("%.1f", ppmValue),
                leftPadding - 8.dp.toPx(),
                yPos + 5.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 8.dp.toPx()
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }

        // Dibujar etiquetas en el eje Y secundario (derecho - ADC)
        for (i in 0..yLabelCount) {
            val yPos = height - padding - (i.toFloat() / yLabelCount) * (height - 2 * padding)
            val adcValue = minADC + (i.toFloat() / yLabelCount) * adcRange

            // Línea de marca
            drawLine(
                color = Color.LightGray,
                start = Offset(width - rightPadding, yPos),
                end = Offset(width - rightPadding + 5.dp.toPx(), yPos),
                strokeWidth = 1.dp.toPx()
            )

            // Texto del valor ADC
            drawContext.canvas.nativeCanvas.drawText(
                adcValue.toInt().toString(),
                width - rightPadding + 7.dp.toPx(),
                yPos + 5.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 8.dp.toPx()
                    textAlign = android.graphics.Paint.Align.LEFT
                }
            )
        }

        // Etiquetas para los ejes
        drawContext.canvas.nativeCanvas.drawText(
            "${gasType.name} (PPM)",
            padding,
            padding - 10.dp.toPx(),
            android.graphics.Paint().apply {
                color = gasColor.toArgb()
                textSize = 10.dp.toPx()
                textAlign = android.graphics.Paint.Align.LEFT
                isFakeBoldText = true
            }
        )

        drawContext.canvas.nativeCanvas.drawText(
            "ADC",
            width - rightPadding + 5.dp.toPx(),
            padding - 10.dp.toPx(),
            android.graphics.Paint().apply {
                color = android.graphics.Color.RED
                textSize = 10.dp.toPx()
                textAlign = android.graphics.Paint.Align.LEFT
                isFakeBoldText = true
            }
        )
    }
}