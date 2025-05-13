// DataPoint.kt
package com.example.mq7server

/**
 * Representa un punto de datos para los gráficos.
 * Estos puntos se utilizan para mostrar datos en tiempo real en los gráficos.
 */
data class DataPoint(
    val x: Float,
    val y: Float
)