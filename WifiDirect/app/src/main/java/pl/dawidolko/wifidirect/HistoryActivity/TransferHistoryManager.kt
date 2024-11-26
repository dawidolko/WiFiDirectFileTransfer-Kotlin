package pl.dawidolko.wifidirect.HistoryActivity

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: Klasa odpowiedzialna za zarządzanie historią transferów plików, w tym zapisywanie i odczytywanie
 * z pamięci lokalnej aplikacji.
 */

object TransferHistoryManager {

    // Lista przechowująca historię przesyłanych i odebranych plików
    private val historyList = mutableListOf<HistoryItem>()

    // Funkcja dodająca plik do historii
    fun addHistoryItem(item: HistoryItem) {
        historyList.add(item)
    }

    // Funkcja do uzyskania wszystkich danych historii
    fun getTransferHistory(): List<HistoryItem> {
        return historyList
    }
}
