package nl.gebaren.app

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener2
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GebarenService : AccessibilityService() {
    private lateinit var sm: SensorManager
    private lateinit var sensoren: Sensoren
    private lateinit var instellingen: Instellingen
    private lateinit var acties: Acties
    private lateinit var wakeLock: PowerManager.WakeLock
    private val handler = Handler(Looper.getMainLooper())
    private val herkenner = Herkenner()

    private var schudGevoeligheid: Int? = null
    private var draaiGevoeligheid: Int? = null
    private var kijkenTot = 0L
    private var kijkt = false
    private var laatsteDubbeltik = 0L
    private var rustTot = 0L
    private var inRust = false

    override fun onServiceConnected() {
        sm = getSystemService(SensorManager::class.java)
        sensoren = Sensoren(sm)
        instellingen = Instellingen(this)
        acties = Acties(this)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gebaren:kijken")
            .apply { setReferenceCounted(false) }
        // Zonder voorgrondstatus komen er met het scherm uit na een minuut geen sensorevents meer.
        runCatching { naarVoorgrond() }.onFailure { Log.w(TAG, "voorgrond mislukt", it) }
        instellingen.prefs.registerOnSharedPreferenceChangeListener(prefsVolger)
        registreer()
    }

    private val prefsVolger = SharedPreferences.OnSharedPreferenceChangeListener { _, sleutel ->
        if (sleutel != Instellingen.LAATSTE) registreer()
    }

    private fun registreer() {
        sm.unregisterListener(beweging)
        sm.unregisterListener(trigger)
        schudGevoeligheid = if (actief(Gebaar.SCHUDDEN)) instellingen.gevoeligheid(Gebaar.SCHUDDEN) else null
        draaiGevoeligheid = if (actief(Gebaar.DRAAIEN)) instellingen.gevoeligheid(Gebaar.DRAAIEN) else null

        if (schudGevoeligheid != null || draaiGevoeligheid != null) {
            sensoren.accel?.let { sm.registerListener(beweging, it, BEMONSTERING_50HZ_US, MAX_BUFFERTIJD_US) }
            if (draaiGevoeligheid != null) sensoren.gyro?.let { sm.registerListener(beweging, it, BEMONSTERING_50HZ_US, MAX_BUFFERTIJD_US) }
            sensoren.triggers.forEach { sm.registerListener(trigger, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }
        if (actief(Gebaar.DUBBELTIK)) {
            sensoren.dubbeltik?.let { sm.registerListener(trigger, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }
        Log.i(TAG, "schudden=$schudGevoeligheid draaien=$draaiGevoeligheid triggers=${sensoren.triggers.map { it.stringType }}")
    }

    private fun actief(g: Gebaar) = sensoren.beschikbaar(g) && instellingen.actie(g) != Actie.NIETS

    private val trigger = object : SensorEventListener2 {
        override fun onSensorChanged(e: SensorEvent) {
            if (e.sensor == sensoren.dubbeltik) {
                if (e.timestamp - laatsteDubbeltik > DUBBELTIK_ONDERDRUKKING_NS) {
                    laatsteDubbeltik = e.timestamp
                    herkend(Gebaar.DUBBELTIK)
                }
            } else {
                if (inRust) rustTot = SystemClock.elapsedRealtime() + STIL_NA_GEBAAR_MS
                kijkEvenOfHetEenGebaarIs()
            }
        }
        override fun onAccuracyChanged(s: Sensor, a: Int) {}
        override fun onFlushCompleted(s: Sensor) {}
    }

    private val beweging = object : SensorEventListener2 {
        override fun onSensorChanged(e: SensorEvent) {
            val v = e.values
            if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) herkenner.accel(e.timestamp, v[0], v[1], v[2])
            else herkenner.gyro(e.timestamp, v[0], v[1], v[2])
        }
        override fun onAccuracyChanged(s: Sensor, a: Int) {}
        override fun onFlushCompleted(s: Sensor) {
            if (s.type == Sensor.TYPE_ACCELEROMETER) beoordeel()
        }
    }

    private fun kijkEvenOfHetEenGebaarIs() {
        kijkenTot = SystemClock.elapsedRealtime() + KIJKDUUR_MS
        if (kijkt) return
        kijkt = true
        wakeLock.acquire(KIJKDUUR_MS + 2_000)
        handler.post(leegtrekken)
    }

    private val leegtrekken = object : Runnable {
        override fun run() {
            if (SystemClock.elapsedRealtime() > kijkenTot) {
                kijkt = false
                wakeLock.release()
                return
            }
            sm.flush(beweging)
            handler.postDelayed(this, FLUSH_INTERVAL_MS)
        }
    }

    private fun beoordeel() {
        if (SystemClock.elapsedRealtime() < rustTot) {
            herkenner.vergeetAllesTotNu()
            return
        }
        inRust = false
        val g = herkenner.beoordeel(SystemClock.elapsedRealtimeNanos(), schudGevoeligheid, draaiGevoeligheid, instellingen.schudAantal) ?: return
        herkenner.vergeetAllesTotNu()
        inRust = true
        rustTot = SystemClock.elapsedRealtime() + STIL_NA_GEBAAR_MS
        herkend(g)
    }

    private fun herkend(g: Gebaar) {
        val actie = instellingen.actie(g)
        Log.i(TAG, "herkend: $g → $actie")
        tril(instellingen.trilling)
        acties.voerUit(actie)
        instellingen.laatste = g to SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())
    }

    private fun naarVoorgrond() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(KANAAL, getString(R.string.melding_kanaal), NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false) },
        )
        val openen = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val melding = Notification.Builder(this, KANAAL)
            .setSmallIcon(R.drawable.ic_melding)
            .setContentTitle(getString(R.string.melding_titel))
            .setContentIntent(openen)
            .setOngoing(true)
            .build()
        startForeground(1, melding, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        if (::sm.isInitialized) {
            sm.unregisterListener(beweging)
            sm.unregisterListener(trigger)
            instellingen.prefs.unregisterOnSharedPreferenceChangeListener(prefsVolger)
            acties.stop()
            handler.removeCallbacks(leegtrekken)
            if (wakeLock.isHeld) wakeLock.release()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Gebaren"
        private const val KANAAL = "actief"
        private const val BEMONSTERING_50HZ_US = 20_000
        private const val MAX_BUFFERTIJD_US = 20_000_000
        private const val DUBBELTIK_ONDERDRUKKING_NS = 800_000_000L
        private const val KIJKDUUR_MS = 1_200L
        private const val FLUSH_INTERVAL_MS = 250L
        private const val STIL_NA_GEBAAR_MS = 1_000L
    }
}
