package com.example.examenfinal
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.examenfinal.db.AppDatabase
import com.example.examenfinal.db.LocationEntity
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.annotation.SuppressLint

class LocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // Intervalo por defecto: 10 segundos (se recibe por Intent)
    private var currentInterval: Long = 10000

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Callback que recibe las coordenadas
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.forEach { location ->
                    // Guardar en Base de Datos (Requisito 3)
                    saveLocationToDb(location.latitude, location.longitude, location.accuracy)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Obtener intervalo seleccionado (Requisito 1: Intervalos configurables)
                currentInterval = intent.getLongExtra(EXTRA_INTERVAL, 10000)
                startForegroundService()
                startLocationUpdates()
            }
            ACTION_STOP -> {
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        val channelId = "location_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // SOLO crear el canal si es Android 8 (Oreo) o superior
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Rastreo Activo",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        // Notificación persistente (Requisito: Indicador visual ON)
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Rastreo de ubicación activo")
            .setContentText("Guardando ubicación cada ${currentInterval / 1000} seg")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true) // Persistente
            .build()

        startForeground(1, notification)
    }
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // Configuración del request de ubicación
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, currentInterval)
            .setMinUpdateIntervalMillis(currentInterval)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // Manejar falta de permisos si es necesario
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun saveLocationToDb(lat: Double, lng: Double, acc: Float) {
        serviceScope.launch {
            val dao = AppDatabase.getDatabase(applicationContext).locationDao()
            val entity = LocationEntity(
                latitude = lat,
                longitude = lng,
                timestamp = System.currentTimeMillis(),
                accuracy = acc // Requisito 5: Guardar precisión
            )
            dao.insertLocation(entity)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_INTERVAL = "EXTRA_INTERVAL"
    }
}