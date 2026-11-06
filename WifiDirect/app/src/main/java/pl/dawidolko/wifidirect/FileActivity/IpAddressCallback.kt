package pl.dawidolko.wifidirect.FileActivity

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: Callback interface used to obtain the receiver's IP address
 * during a Wi-Fi Direct connection.
 */

interface IpAddressCallback {
    fun onIpAddressReceived(ipAddress: String)
}
