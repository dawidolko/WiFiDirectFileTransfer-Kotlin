package pl.dawidolko.wifidirect.TrustedDevicesActivity

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import pl.dawidolko.wifidirect.R
import pl.dawidolko.wifidirect.TrustedDevice

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: Adapter RecyclerView używany do wyświetlania listy zaufanych urządzeń
 * w TrustedDevicesActivity.
 */

class TrustedDevicesAdapter(private val devices: MutableList<TrustedDevice>) :
    RecyclerView.Adapter<TrustedDevicesAdapter.TrustedDeviceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrustedDeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trusted_device, parent, false)
        return TrustedDeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrustedDeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.deviceName.text = device.deviceName
        holder.macAddress.text = device.macAddress
    }

    override fun getItemCount(): Int {
        return devices.size
    }

    // Dodajemy metodę, która dodaje urządzenie do listy
    fun addDevice(device: TrustedDevice) {
        devices.add(device)
        notifyItemInserted(devices.size - 1)
    }

    inner class TrustedDeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceName: TextView = itemView.findViewById(R.id.device_item_title)
        val macAddress: TextView = itemView.findViewById(R.id.device_item_subtitle)
    }
}
