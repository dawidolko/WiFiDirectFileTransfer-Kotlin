// TrustedDevice.kt
package pl.dawidolko.wifidirect

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: Klasa modelu danych reprezentująca zaufane urządzenie zapisane w aplikacji.
 * Przechowuje informacje o nazwie i adresie MAC urządzenia.
 */

data class TrustedDevice(
    val deviceName: String,
    val macAddress: String
)
