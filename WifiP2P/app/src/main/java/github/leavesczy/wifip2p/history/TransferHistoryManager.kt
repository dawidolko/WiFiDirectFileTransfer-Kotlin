package github.leavesczy.wifip2p.common

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object TransferHistoryManager {

    private const val PREF_NAME = "transfer_history"
    private const val KEY_HISTORY = "history"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Add a new record to the file transfer history.
     *
     * @param context Application context
     * @param record Record to add (e.g. "Sent: file.jpg")
     */
    fun addTransferRecord(context: Context, record: String) {
        val prefs = getPrefs(context)
        val history = prefs.getStringSet(KEY_HISTORY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        history.add(record)
        prefs.edit().putStringSet(KEY_HISTORY, history).apply()
        Log.d("TransferHistoryManager", "Added record: $record")
    }

    fun getTransferHistory(context: Context): List<String> {
        val prefs = getPrefs(context)
        val history = prefs.getStringSet(KEY_HISTORY, emptySet())
        Log.d("TransferHistoryManager", "Retrieved history: $history")
        return history?.toList() ?: emptyList()
    }

    /**
     * Clear the whole file transfer history.
     *
     * @param context Application context
     */
    fun clearHistory(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    /**
     * Remove the given record from the history.
     *
     * @param context Application context
     * @param record Record to remove
     */
    fun removeTransferRecord(context: Context, record: String) {
        val prefs = getPrefs(context)
        val history = prefs.getStringSet(KEY_HISTORY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (history.remove(record)) {
            prefs.edit().putStringSet(KEY_HISTORY, history).apply()
        }
    }
}
