package nl.gebaren.app

import kotlin.math.abs

class Herkenner {
    private val accel = Ring(VENSTER_MONSTERS)
    private val gyro = Ring(VENSTER_MONSTERS)
    private var niksVoor = 0L

    fun accel(t: Long, x: Float, y: Float, z: Float) = accel.voegToe(t, x, y, z)
    fun gyro(t: Long, x: Float, y: Float, z: Float) = gyro.voegToe(t, x, y, z)

    fun vergeetAllesTotNu() {
        niksVoor = maxOf(accel.laatste(), gyro.laatste()) + 1
    }

    fun beoordeel(nu: Long, schudGevoeligheid: Int?, draaiGevoeligheid: Int?, schudAantal: Int = 2): Gebaar? {
        val vanaf = maxOf(nu - VENSTER_NS, niksVoor)
        if (draaiGevoeligheid != null && isDraaien(gyro.sinds(vanaf), DRAAI_DREMPEL_RAD_S[stand(draaiGevoeligheid)])) return Gebaar.DRAAIEN
        if (schudGevoeligheid != null && isSchudden(accel.sinds(vanaf), SCHUD_DREMPEL_MS2[stand(schudGevoeligheid)], schudAantal)) return Gebaar.SCHUDDEN
        return null
    }

    private fun stand(gevoeligheid: Int) = (gevoeligheid - 1).coerceIn(0, 4)

    private fun isDraaien(g: List<FloatArray>, drempel: Float): Boolean {
        if (g.size < 10) return false
        val draaiingPerAs = FloatArray(3)
        for (m in g) for (i in 0..2) draaiingPerAs[i] += abs(m[i])
        val dwars = (0..2).filter { it != LENGTEAS }.maxOf { draaiingPerAs[it] }
        if (draaiingPerAs[LENGTEAS] < DRAAI_OVERHEERSING * dwars) return false
        return wisselingen(g, LENGTEAS, drempel) >= 3
    }

    private fun isSchudden(a: List<FloatArray>, drempel: Float, aantal: Int): Boolean {
        if (a.size < 10) return false
        val zwaartekracht = FloatArray(3)
        for (m in a) for (i in 0..2) zwaartekracht[i] += m[i] / a.size
        val beweging = a.map { m -> FloatArray(3) { m[it] - zwaartekracht[it] } }
        val energie = FloatArray(3)
        for (m in beweging) for (i in 0..2) energie[i] += m[i] * m[i]
        val as_ = energie.indices.maxBy { energie[it] }
        return wisselingen(beweging, as_, drempel) >= if (aantal <= 1) 3 else 5
    }

    private fun wisselingen(reeks: List<FloatArray>, as_: Int, drempel: Float): Int {
        var teken = 0
        var aantal = 0
        for (m in reeks) {
            val v = m[as_]
            val nieuw = when {
                v > drempel -> 1
                v < -drempel -> -1
                else -> continue
            }
            if (nieuw != teken) {
                teken = nieuw
                aantal++
            }
        }
        return aantal
    }

    private class Ring(val grootte: Int) {
        private val tijden = LongArray(grootte)
        private val waarden = Array(grootte) { FloatArray(3) }
        private var volgende = 0
        private var aantal = 0

        fun voegToe(t: Long, x: Float, y: Float, z: Float) {
            tijden[volgende] = t
            waarden[volgende].let { it[0] = x; it[1] = y; it[2] = z }
            volgende = (volgende + 1) % grootte
            if (aantal < grootte) aantal++
        }

        fun laatste(): Long = if (aantal == 0) 0L else tijden[(volgende - 1 + grootte) % grootte]

        fun sinds(vanaf: Long): List<FloatArray> {
            val uit = ArrayList<FloatArray>(aantal)
            for (k in 0 until aantal) {
                val i = (volgende - aantal + k + grootte) % grootte
                if (tijden[i] >= vanaf) uit += waarden[i]
            }
            return uit
        }
    }

    companion object {
        private const val LENGTEAS = 1
        const val VENSTER_NS = 1_500_000_000L
        private const val VENSTER_MONSTERS = 300
        private val DRAAI_DREMPEL_RAD_S = floatArrayOf(6f, 5f, 4f, 3f, 2.5f)
        private const val DRAAI_OVERHEERSING = 1.5f
        private val SCHUD_DREMPEL_MS2 = floatArrayOf(25f, 20f, 15f, 11f, 8f)
    }
}
