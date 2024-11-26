package pl.dawidolko.wifidirect.FileActivity

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: Interfejs callback wykorzystywany do uzyskania adresu IP urządzenia odbiorcy
 * podczas połączenia za pomocą Wi-Fi Direct.
 */

interface IpAddressCallback {
    fun onIpAddressReceived(ipAddress: String)
}
