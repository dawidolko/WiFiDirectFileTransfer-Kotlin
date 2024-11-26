package github.leavesczy.wifip2p.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import github.leavesczy.wifip2p.R

class TransferHistoryAdapter(private val historyList: List<String>) :
    RecyclerView.Adapter<TransferHistoryAdapter.ViewHolder>() {

    fun updateData(newData: List<String>) {
        var historyData = newData
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHistoryItem: TextView = view.findViewById(R.id.tvHistoryItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvHistoryItem.text = historyList[position]
    }

    override fun getItemCount(): Int = historyList.size
}
