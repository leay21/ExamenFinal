package com.example.examenfinal


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.examenfinal.db.AppDatabase
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var tvCoordinates: TextView
    private lateinit var btnStartStop: Button
    private lateinit var tvStatus: TextView
    private var isTracking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Lógica simple para cambiar tema (puedes expandirla si sobra tiempo)
        // Por defecto cargará el Guinda definido en el Manifest.
        // Si quieres probar el azul, descomenta la siguiente línea:
        // setTheme(R.style.Theme_RastreoExamen_Azul)
        super.onCreate(savedInstanceState)

        // Configuración necesaria para OSM
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        setContentView(R.layout.activity_main)

        // Referencias UI
        map = findViewById(R.id.map)
        tvCoordinates = findViewById(R.id.tvCoordinates)
        btnStartStop = findViewById(R.id.btnStartStop)
        tvStatus = findViewById(R.id.tvStatus)
        val rgInterval = findViewById<RadioGroup>(R.id.rgInterval)
        val btnClear = findViewById<Button>(R.id.btnClear)

        // Configurar Mapa [cite: 24]
        map.setMultiTouchControls(true)
        map.controller.setZoom(18.0)
        // Punto inicial por defecto (ESCOM)
        map.controller.setCenter(GeoPoint(19.5045, -99.1469))

        // Permisos [cite: 54]
        checkPermissions()

        // Botón Iniciar/Detener [cite: 43]
        btnStartStop.setOnClickListener {
            if (isTracking) {
                stopTracking()
            } else {
                val interval = when (rgInterval.checkedRadioButtonId) {
                    R.id.rb10s -> 10000L
                    R.id.rb60s -> 60000L
                    R.id.rb5min -> 300000L
                    else -> 10000L
                }
                startTracking(interval)
            }
        }

        // Botón Limpiar Historial [cite: 52]
        btnClear.setOnClickListener {
            lifecycleScope.launch {
                AppDatabase.getDatabase(this@MainActivity).locationDao().clearHistory()
                map.overlays.clear()
                map.invalidate()
                Toast.makeText(this@MainActivity, "Historial borrado", Toast.LENGTH_SHORT).show()
            }
        }

        // Observar base de datos para actualizar mapa y UI [cite: 27, 26]
        observeLocationUpdates()
    }

    private fun startTracking(interval: Long) {
        val intent = Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_START
            putExtra(LocationService.EXTRA_INTERVAL, interval)
        }
        // --- CORRECCIÓN ---
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // ------------------
        isTracking = true
        updateUIState()
    }

    private fun stopTracking() {
        val intent = Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP
        }
        startService(intent)
        isTracking = false
        updateUIState()
    }

    private fun updateUIState() {
        btnStartStop.text = if (isTracking) "Detener Rastreo" else "Iniciar Rastreo"
        tvStatus.text = if (isTracking) "Estado: ACTIVO" else "Estado: INACTIVO"
        tvStatus.setTextColor(if (isTracking) Color.GREEN else Color.RED)
    }

    private fun observeLocationUpdates() {
        val dao = AppDatabase.getDatabase(this).locationDao()
        val polyline = Polyline().apply {
            outlinePaint.color = Color.BLUE // Color ESCOM
            outlinePaint.strokeWidth = 10f
        }
        map.overlays.add(polyline)

        lifecycleScope.launch {
            dao.getAllLocations().collect { locations ->
                if (locations.isNotEmpty()) {
                    val latest = locations.first()

                    // Actualizar texto [cite: 41]
                    tvCoordinates.text = "Lat: ${latest.latitude}\nLon: ${latest.longitude}\nPrecisión: ${latest.accuracy}m"

                    // Dibujar Ruta
                    val geoPoints = locations.map { GeoPoint(it.latitude, it.longitude) }
                    polyline.setPoints(geoPoints)

                    // Mover mapa y marcador a la última posición [cite: 25]
                    val currentPoint = GeoPoint(latest.latitude, latest.longitude)
                    map.controller.animateTo(currentPoint)

                    // Marcador actual
                    // (Limpiamos marcadores viejos para no llenar el mapa, dejamos solo el actual y la ruta)
                    // Nota: En una app real, gestiona mejor los overlays.
                    map.overlays.removeAll { it is Marker }
                    val marker = Marker(map)
                    marker.position = currentPoint
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.title = "Aquí estás"
                    map.overlays.add(marker)
                    map.invalidate()
                }
            }
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }
    }
}