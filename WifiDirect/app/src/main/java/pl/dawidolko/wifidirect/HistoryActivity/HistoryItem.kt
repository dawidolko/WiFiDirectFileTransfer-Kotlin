package pl.dawidolko.wifidirect.HistoryActivity

import java.io.Serializable

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: Klasa modelu danych reprezentująca pojedynczy element historii transferu plików.
 * Przechowuje takie informacje jak nazwa pliku, data transferu oraz status wysyłki/odbioru.
 */

data class HistoryItem(
    val fileName: String,
    val timestamp: String,
    val isSent: Boolean // true - wysłany, false - odebrany
) : Serializable
