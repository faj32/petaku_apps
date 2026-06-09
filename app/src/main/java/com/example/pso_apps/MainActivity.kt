package com.example.pso_apps

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.net.http.SslError
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

class MainActivity : AppCompatActivity() {

    private val TAG = "PSO_GPS"
    private var panelExpanded = true
    private lateinit var mapWebView: WebView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null  // nullable, bukan lateinit
    private var lastLocation: Location? = null
    private val MIN_DISTANCE_METER = 10f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapWebView                    = findViewById(R.id.mapWebView)
        val inputLokasi: EditText     = findViewById(R.id.inputLokasi)
        val btnProses: Button         = findViewById(R.id.btnProses)
        val kontenPanel: LinearLayout = findViewById(R.id.kontenPanel)
        val btnToggle: LinearLayout   = findViewById(R.id.btnTogglePanel)
        val iconPanah: ImageView      = findViewById(R.id.iconPanah)

        // ── Setup WebView dulu sebelum apapun ──
        setupWebView()

        // ── Init Fused Location dengan try-catch ──
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            // Callback diinit SEBELUM requestGpsPermission
            setupLocationCallback()
            requestGpsPermission()
        } catch (e: Exception) {
            Log.e(TAG, "FusedLocation gagal init: ${e.message}")
            // Fallback: biarkan watchPosition di JS yang handle GPS
        }

        // ── Tombol Rute Optimal ──
        btnProses.setOnClickListener {
            val alamat = inputLokasi.text.toString().trim()
            if (alamat.isNotEmpty()) {
                collapsePanel(kontenPanel, iconPanah)
                mapWebView.evaluateJavascript(
                    "javascript:hitungRuteMutar('${alamat.replace("'", "\\'")}')",
                    null
                )
            } else {
                inputLokasi.error = "Masukkan minimal 1 alamat tujuan!"
            }
        }

        // ── Toggle panel ──
        btnToggle.setOnClickListener {
            if (panelExpanded) collapsePanel(kontenPanel, iconPanah)
            else expandPanel(kontenPanel, iconPanah)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { moveTaskToBack(true) }
        })
    }

    // SETUP WEBVIEW
    private fun setupWebView() {
        mapWebView.settings.apply {
            javaScriptEnabled                = true
            domStorageEnabled                = true
            databaseEnabled                  = true
            allowFileAccessFromFileURLs      = true
            allowUniversalAccessFromFileURLs = true
            setGeolocationEnabled(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        mapWebView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }
        }

        mapWebView.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(
                view: WebView?, handler: SslErrorHandler?, error: SslError?
            ) { handler?.proceed() }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Halaman HTML sudah siap — kirim posisi terakhir jika ada
                lastLocation?.let {
                    kirimGpsKeJS(it.latitude, it.longitude, it.accuracy)
                }
            }
        }

        mapWebView.loadUrl("file:///android_asset/index.html")
    }

    // SETUP LOCATION CALLBACK
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val prev = lastLocation
                if (prev != null && loc.distanceTo(prev) < MIN_DISTANCE_METER) return
                lastLocation = loc
                kirimGpsKeJS(loc.latitude, loc.longitude, loc.accuracy)
            }
        }
    }

    // KIRIM GPS KE JAVASCRIPT
    private fun kirimGpsKeJS(lat: Double, lon: Double, akurasi: Float) {
        val js = "javascript:updateGPSdariKotlin($lat, $lon, $akurasi)"
        runOnUiThread {
            try {
                mapWebView.evaluateJavascript(js, null)
                Log.d(TAG, "GPS dikirim ke JS: $lat, $lon (akurasi: $akurasi)")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal kirim GPS ke JS: ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════
    // START LOCATION UPDATES
    // ═══════════════════════════════════════
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val cb = locationCallback ?: return  // guard: jangan jalan kalau callback null

        try {
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                3000L
            )
                .setMinUpdateDistanceMeters(MIN_DISTANCE_METER)
                .setWaitForAccurateLocation(false)
                .build()

            fusedLocationClient.requestLocationUpdates(request, cb, Looper.getMainLooper())

            // Ambil posisi terakhir yang diketahui
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    lastLocation = it
                    kirimGpsKeJS(it.latitude, it.longitude, it.accuracy)
                }
            }

            Log.d(TAG, "Location updates dimulai")
        } catch (e: Exception) {
            Log.e(TAG, "startLocationUpdates error: ${e.message}")
        }
    }

    // ═══════════════════════════════════════
    // PERMISSION
    // ═══════════════════════════════════════
    private fun requestGpsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        }
    }

    // ═══════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════
    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        } catch (e: Exception) {
            Log.e(TAG, "onPause removeUpdates error: ${e.message}")
        }
    }

    // ═══════════════════════════════════════
    // PANEL
    // ═══════════════════════════════════════
    private fun collapsePanel(konten: LinearLayout, panah: ImageView) {
        konten.visibility = View.GONE
        panah.setImageResource(android.R.drawable.arrow_down_float)
        panelExpanded = false
    }

    private fun expandPanel(konten: LinearLayout, panah: ImageView) {
        konten.visibility = View.VISIBLE
        panah.setImageResource(android.R.drawable.arrow_up_float)
        panelExpanded = true
    }
}