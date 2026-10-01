package nl.gebaren.app

import android.hardware.Sensor
import android.hardware.SensorManager

class Sensoren(sm: SensorManager) {
    private val alle = sm.getSensorList(Sensor.TYPE_ALL)

    val accel: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val gyro: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    val dubbeltik: Sensor? = zoek("xiaomi.sensor.dbtap")

    val triggers: List<Sensor> = listOfNotNull(
        zoek("android.sensor.tilt_detector"),
        zoek("android.sensor.device_orientation"),
        zoek("xiaomi.sensor.pickup"),
        zoek("xiaomi.sensor.oem_shkcam"),
    )

    fun beschikbaar(g: Gebaar) = when (g) {
        Gebaar.SCHUDDEN -> accel != null
        Gebaar.DRAAIEN -> accel != null && gyro != null
        Gebaar.DUBBELTIK -> dubbeltik != null
    }

    private fun zoek(type: String): Sensor? {
        val passend = alle.filter { it.stringType == type }
        return passend.firstOrNull { it.isWakeUpSensor } ?: passend.firstOrNull()
    }
}
