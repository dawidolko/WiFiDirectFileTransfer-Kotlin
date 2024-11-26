package github.leavesczy.wifip2p

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AlertDialog

class LoadingDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "LoadingDialogFragment"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setMessage("Loading...")
            .setCancelable(false)
            .create()
    }
}
