package github.leavesczy.wifip2p

import android.util.Log

/**
 * @Author: dawidolko
 * @Date: 2024/11/11
 * @Desc:
 */
object Logger {

    fun log(any: Any?) {
        Log.e("WifiP2P", any?.toString() ?: "null")
    }

}