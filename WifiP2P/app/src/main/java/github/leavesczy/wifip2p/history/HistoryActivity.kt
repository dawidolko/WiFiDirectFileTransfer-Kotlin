package github.leavesczy.wifip2p

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import github.leavesczy.wifip2p.common.TransferHistoryAdapter
import github.leavesczy.wifip2p.common.TransferHistoryManager

class HistoryActivity : AppCompatActivity() {

    private lateinit var rvHistoryList: RecyclerView
    private lateinit var tvEmptyMessage: TextView
    private lateinit var adapter: TransferHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("HistoryActivity", "onCreate started")
        setContentView(R.layout.activity_history)

        // Konfiguracja ActionBar
        supportActionBar?.apply {
            title = "History"
            setDisplayHomeAsUpEnabled(true)
        }

        // Inicjalizacja elementów UI
        rvHistoryList = findViewById(R.id.rvHistoryList)
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)

        // Konfiguracja RecyclerView
        rvHistoryList.layoutManager = LinearLayoutManager(this)
        adapter = TransferHistoryAdapter(emptyList())
        rvHistoryList.adapter = adapter

        // Załaduj dane
        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        Log.d("HistoryActivity", "onResume started")
        // Ponowne załadowanie danych przy wznowieniu aktywności
        loadHistory()
    }

    private fun loadHistory() {
        try {
            val historyData = TransferHistoryManager.getTransferHistory(this)
            if (historyData.isEmpty()) {
                showEmptyMessage()
            } else {
                showHistory(historyData)
            }
        } catch (e: Exception) {
            Log.e("HistoryActivity", "Error loading history", e)
            Toast.makeText(this, "Error loading history", Toast.LENGTH_LONG).show()
            showEmptyMessage()
        }
    }

    private fun showEmptyMessage() {
        tvEmptyMessage.text = "No transfer history available."
        tvEmptyMessage.visibility = View.VISIBLE
        rvHistoryList.visibility = View.GONE
    }

    private fun showHistory(historyData: List<String>) {
        adapter.updateData(historyData)
        tvEmptyMessage.visibility = View.GONE
        rvHistoryList.visibility = View.VISIBLE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
