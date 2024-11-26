package pl.dawidolko.wifidirect.HistoryActivity

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import pl.dawidolko.wifidirect.R

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: Adapter RecyclerView do wyświetlania listy historii transferów plików w HistoryActivity.
 */

class TransferHistoryAdapter(private val historyList: List<HistoryItem>) : RecyclerView.Adapter<TransferHistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvSentStatus: TextView = itemView.findViewById(R.id.tvSentStatus)

        fun bind(historyItem: HistoryItem) {
            tvFileName.text = historyItem.fileName
            tvTimestamp.text = historyItem.timestamp
            tvSentStatus.text = if (historyItem.isSent) "Sent" else "Received"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.history_item_layout, parent, false)
        return HistoryViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val historyItem = historyList[position]
        holder.bind(historyItem)
    }

    override fun getItemCount(): Int = historyList.size
}
