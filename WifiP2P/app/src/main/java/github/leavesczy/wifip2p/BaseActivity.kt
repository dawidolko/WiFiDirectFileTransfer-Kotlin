import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import github.leavesczy.wifip2p.LoadingDialogFragment

open class BaseActivity : AppCompatActivity() {

    private var loadingDialog: DialogFragment? = null

    protected fun showLoadingDialog() {
        if (loadingDialog == null) {
            loadingDialog = LoadingDialogFragment()
            loadingDialog?.show(supportFragmentManager, LoadingDialogFragment.TAG)
        }
    }

    protected fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    protected fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    protected fun <T : Activity> startActivity(clazz: Class<T>) {
        val intent = Intent(this, clazz)
        startActivity(intent)
    }
}
