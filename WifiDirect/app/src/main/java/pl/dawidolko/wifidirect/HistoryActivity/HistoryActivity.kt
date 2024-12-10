package pl.dawidolko.wifidirect.HistoryActivity

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import pl.dawidolko.wifidirect.R

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: Activity odpowiedzialne za wyświetlanie historii transferów plików, zarówno wysłanych, jak i odebranych.
 * Zawiera mechanizmy zarządzania oraz filtrowania historii.
 */

class HistoryActivity : AppCompatActivity() {

    private lateinit var rvHistoryList: RecyclerView
    private lateinit var adapter: TransferHistoryAdapter
    private var historyList = mutableListOf<HistoryItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        supportActionBar?.apply {
            title = "File History"
            setDisplayHomeAsUpEnabled(true)
        }

        rvHistoryList = findViewById(R.id.rvHistoryList)
        rvHistoryList.layoutManager = LinearLayoutManager(this)

        loadHistory()

        if (historyList.isEmpty()) {
            findViewById<TextView>(R.id.tvEmptyMessage).visibility = View.VISIBLE
            // Możesz ukryć RecyclerView jeśli chcesz:
            // findViewById<RecyclerView>(R.id.rvHistoryList).visibility = View.GONE
        } else {
            findViewById<TextView>(R.id.tvEmptyMessage).visibility = View.GONE
            // findViewById<RecyclerView>(R.id.rvHistoryList).visibility = View.VISIBLE
        }

        adapter = TransferHistoryAdapter(historyList)
        rvHistoryList.adapter = adapter
    }

    private fun loadHistory() {
        val sharedPreferences = getSharedPreferences("file_history", MODE_PRIVATE)
        val historyListJson = sharedPreferences.getString("history_list", "[]")

        val gson = Gson()
        val historyItems = gson.fromJson(historyListJson, Array<HistoryItem>::class.java)

        historyList = historyItems.toMutableList()
    }

    // Obsługa kliknięcia na strzałkę wstecz w ActionBar
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
