// TrustedDevice.kt
package pl.dawidolko.wifidirect

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: Data model for a trusted device saved in the app.
 * Holds the device name and MAC address.
 */

data class TrustedDevice(
    val deviceName: String,
    val macAddress: String
)
