package pl.dawidolko.wifidirect

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * @Author: dawidolko
 * @Date: 26.11.2024
 *
 * @Desc: Aktywność startowa wyświetlana podczas uruchamiania aplikacji.
 * Zawiera ekran powitalny lub logo aplikacji.
 */

class SplashActivity : AppCompatActivity() {

    private lateinit var logoImageView: ImageView
    private lateinit var creditsTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Inicjalizacja widoków
        logoImageView = findViewById(R.id.logoImageView)
        creditsTextView = findViewById(R.id.creditsTextView)

        // Animacja powiększenia logo
        val scaleAnimation = ScaleAnimation(
            0f, 2f,
            0f, 2f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        scaleAnimation.duration = 2000
        scaleAnimation.fillAfter = true

        logoImageView.startAnimation(scaleAnimation)

        // Animacja napisu z kredytami
        creditsTextView.apply {
            alpha = 0f
            text = "CREATED BY DAWID OLKO/PIOTR SMOŁA"
            animate().alpha(1f).setDuration(1000).setStartDelay(500).start()
        }

        // Opóźnienie 3 sekundy przed przejściem do MainActivity
        Handler().postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }, 3000)
    }
}
