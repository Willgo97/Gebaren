package nl.gebaren.app

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes

enum class Gebaar(@StringRes val naam: Int, @StringRes val uitleg: Int, val standaard: Actie) {
    SCHUDDEN(R.string.gebaar_schudden, R.string.gebaar_schudden_uitleg, Actie.ZAKLAMP),
    DRAAIEN(R.string.gebaar_draaien, R.string.gebaar_draaien_uitleg, Actie.CAMERA),
    DUBBELTIK(R.string.gebaar_dubbeltik, R.string.gebaar_dubbeltik_uitleg, Actie.NIETS),
}

enum class Actie(@StringRes val naam: Int) {
    NIETS(R.string.actie_niets),
    ZAKLAMP(R.string.actie_zaklamp),
    CAMERA(R.string.actie_camera),
    MEDIA(R.string.actie_media),
    VERGRENDELEN(R.string.actie_vergrendelen),
    SCHERMAFBEELDING(R.string.actie_schermafbeelding),
}

enum class Trilling(@StringRes val naam: Int, val patroon: LongArray) {
    UIT(R.string.trilling_uit, longArrayOf()),
    NORMAAL(R.string.trilling_normaal, longArrayOf(0, 60, 70, 60)),
    STERK(R.string.trilling_sterk, longArrayOf(0, 150, 90, 150)),
    EXTRA(R.string.trilling_extra, longArrayOf(0, 250, 100, 250, 100, 250)),
}

class Instellingen(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("gebaren", Context.MODE_PRIVATE)

    fun actie(g: Gebaar): Actie =
        prefs.getString(g.name, null)?.let { naam -> Actie.entries.find { it.name == naam } } ?: g.standaard

    fun zetActie(g: Gebaar, a: Actie) = prefs.edit().putString(g.name, a.name).apply()

    fun gevoeligheid(g: Gebaar): Int = prefs.getInt("gevoeligheid_${g.name}", 3)

    fun zetGevoeligheid(g: Gebaar, waarde: Int) = prefs.edit().putInt("gevoeligheid_${g.name}", waarde).apply()

    var schudAantal: Int
        get() = prefs.getInt("schud_aantal", 2)
        set(v) = prefs.edit().putInt("schud_aantal", v).apply()

    var trilling: Trilling
        get() = prefs.getString("trilling", null)?.let { n -> Trilling.entries.find { it.name == n } } ?: Trilling.STERK
        set(v) = prefs.edit().putString("trilling", v.name).apply()

    var laatste: Pair<Gebaar, String>?
        get() {
            val delen = prefs.getString(LAATSTE, null)?.split(' ') ?: return null
            val g = Gebaar.entries.find { it.name == delen.getOrNull(0) } ?: return null
            return g to (delen.getOrNull(1) ?: "")
        }
        set(v) = prefs.edit().putString(LAATSTE, v?.let { "${it.first.name} ${it.second}" }).apply()

    companion object {
        const val LAATSTE = "laatste"
    }
}
