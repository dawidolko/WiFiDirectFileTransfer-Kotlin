package pl.dawidolko.wifidirect.HistoryActivity

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: Manages the file transfer history, saving it to and reading it from
 * the app's local storage.
 */

object TransferHistoryManager {

    /*
     * List holding the history of sent and received files
     */
    private val historyList = mutableListOf<HistoryItem>()

    fun addHistoryItem(item: HistoryItem) {
        historyList.add(item)
    }

    fun getTransferHistory(): List<HistoryItem> {
        return historyList
    }
}
