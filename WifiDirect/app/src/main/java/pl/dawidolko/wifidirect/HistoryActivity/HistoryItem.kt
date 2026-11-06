package pl.dawidolko.wifidirect.HistoryActivity

import java.io.Serializable

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: Data model for a single file transfer history item.
 * Holds the file name, transfer date and send/receive status.
 */

data class HistoryItem(
    val fileName: String,
    val timestamp: String,
    /*
     * true - sent, false - received
     */
    val isSent: Boolean
) : Serializable
