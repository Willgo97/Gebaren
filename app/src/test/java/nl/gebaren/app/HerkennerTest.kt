package nl.gebaren.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class HerkennerTest {
    private fun herkennerMetAnderhalveSecondeOp50Hz(
        accel: (Double) -> Triple<Float, Float, Float>,
        gyro: (Double) -> Triple<Float, Float, Float>,
    ): Herkenner {
        val h = Herkenner()
        for (i in 0 until 75) {
            val t = i / 50.0
            val ns = (t * 1e9).toLong()
            accel(t).let { (x, y, z) -> h.accel(ns, x, y, z) }
            gyro(t).let { (x, y, z) -> h.gyro(ns, x, y, z) }
        }
        return h
    }

    private val nu = 1_500_000_000L
    private val rechtopInRust = { _: Double -> Triple(0f, 9.81f, 0f) }
    private val stil = { _: Double -> Triple(0f, 0f, 0f) }

    @Test fun draaienOmDeLengteas() {
        val h = herkennerMetAnderhalveSecondeOp50Hz(rechtopInRust) { t -> Triple(3f, (25 * sin(2 * PI * 1.5 * t)).toFloat(), 4f) }
        assertEquals(Gebaar.DRAAIEN, h.beoordeel(nu, schudGevoeligheid = 3, draaiGevoeligheid = 3))
    }

    @Test fun flinkSchudden() {
        val h = herkennerMetAnderhalveSecondeOp50Hz({ t -> Triple((60 * sin(2 * PI * 5 * t)).toFloat(), 9.81f, 0f) }) { t ->
            Triple(0f, 2f, (20 * sin(2 * PI * 5 * t)).toFloat())
        }
        assertEquals(Gebaar.SCHUDDEN, h.beoordeel(nu, schudGevoeligheid = 3, draaiGevoeligheid = 3))
    }

    @Test fun lopenIsGeenGebaar() {
        val h = herkennerMetAnderhalveSecondeOp50Hz({ t -> Triple(1f, (9.81 + 6 * sin(2 * PI * 2 * t)).toFloat(), 2f) }) { t ->
            Triple((1.5 * sin(2 * PI * 1 * t)).toFloat(), (1.5 * sin(2 * PI * 2 * t)).toFloat(), 0.5f)
        }
        assertNull(h.beoordeel(nu, schudGevoeligheid = 3, draaiGevoeligheid = 3))
    }

    @Test fun stilliggenIsGeenGebaar() {
        assertNull(herkennerMetAnderhalveSecondeOp50Hz(rechtopInRust, stil).beoordeel(nu, schudGevoeligheid = 3, draaiGevoeligheid = 3))
    }

    @Test fun uitgezetGebaarWordtNietHerkend() {
        val h = herkennerMetAnderhalveSecondeOp50Hz(rechtopInRust) { t -> Triple(3f, (25 * sin(2 * PI * 1.5 * t)).toFloat(), 4f) }
        assertNull(h.beoordeel(nu, schudGevoeligheid = 3, draaiGevoeligheid = null))
    }

    @Test fun naHerkennenTeltHetVerledenNietMee() {
        val h = herkennerMetAnderhalveSecondeOp50Hz(rechtopInRust) { t -> Triple(3f, (25 * sin(2 * PI * 1.5 * t)).toFloat(), 4f) }
        h.vergeetAllesTotNu()
        assertNull(h.beoordeel(nu, schudGevoeligheid = 3, draaiGevoeligheid = 3))
    }

    @Test fun lageGevoeligheidNegeertZachtSchudden() {
        val zacht = { t: Double -> Triple((18 * sin(2 * PI * 5 * t)).toFloat(), 9.81f, 0f) }
        assertNull(herkennerMetAnderhalveSecondeOp50Hz(zacht, stil).beoordeel(nu, schudGevoeligheid = 1, draaiGevoeligheid = null))
        assertEquals(Gebaar.SCHUDDEN, herkennerMetAnderhalveSecondeOp50Hz(zacht, stil).beoordeel(nu, schudGevoeligheid = 5, draaiGevoeligheid = null))
    }

    @Test fun eenKeerSchuddenAlleenAlsDatIsIngesteld() {
        val eenKeerHeenEnTerug = { t: Double ->
            val x = if (t < 0.4) 50 * cos(2 * PI * 2.5 * t) else 0.0
            Triple(x.toFloat(), 9.81f, 0f)
        }
        assertNull(herkennerMetAnderhalveSecondeOp50Hz(eenKeerHeenEnTerug, stil).beoordeel(nu, schudGevoeligheid = 3, draaiGevoeligheid = null, schudAantal = 2))
        assertEquals(Gebaar.SCHUDDEN, herkennerMetAnderhalveSecondeOp50Hz(eenKeerHeenEnTerug, stil).beoordeel(nu, schudGevoeligheid = 3, draaiGevoeligheid = null, schudAantal = 1))
    }

    @Test fun snelTrillenZonderEchteHoekIsGeenDraai() {
        val h = herkennerMetAnderhalveSecondeOp50Hz(rechtopInRust) { t -> Triple(1f, (8 * sin(2 * PI * 8 * t)).toFloat(), 1f) }
        assertNull(h.beoordeel(nu, schudGevoeligheid = null, draaiGevoeligheid = 3))
    }

    @Test fun kleineDraaiTeltOpHogeGevoeligheidWelMaarOpNormaalNiet() {
        val klein = { t: Double -> Triple(1f, (6 * sin(2 * PI * 4 * t)).toFloat(), 1f) }
        assertNull(herkennerMetAnderhalveSecondeOp50Hz(rechtopInRust, klein).beoordeel(nu, schudGevoeligheid = null, draaiGevoeligheid = 3))
        assertEquals(Gebaar.DRAAIEN, herkennerMetAnderhalveSecondeOp50Hz(rechtopInRust, klein).beoordeel(nu, schudGevoeligheid = null, draaiGevoeligheid = 5))
    }
}
