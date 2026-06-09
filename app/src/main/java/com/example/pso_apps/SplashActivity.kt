package com.example.pso_apps

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Menahan tampilan Splash selama 3 detik sebelum masuk ke peta utama
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)

            // Transisi fade-in dan fade-out yang halus
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

            // Mengakhiri SplashActivity agar pengguna tidak kembali ke loading saat menekan back
            finish()
        }, 3000)
    }
}