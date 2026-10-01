package nl.gebaren.app

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private lateinit var instellingen: Instellingen
    private lateinit var sensoren: Sensoren
    private var aan by mutableStateOf(false)
    private var laatste by mutableStateOf<Pair<Gebaar, String>?>(null)

    private val laatsteVolger = SharedPreferences.OnSharedPreferenceChangeListener { _, sleutel ->
        if (sleutel == Instellingen.LAATSTE) laatste = instellingen.laatste
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        instellingen = Instellingen(this)
        sensoren = Sensoren(getSystemService(SensorManager::class.java))
        setContent {
            val donker = isSystemInDarkTheme()
            val ctx = LocalContext.current
            MaterialTheme(colorScheme = if (donker) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) {
                Surface(Modifier.fillMaxSize()) {
                    Scherm(aan, laatste)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        aan = serviceAan()
        laatste = instellingen.laatste
        instellingen.prefs.registerOnSharedPreferenceChangeListener(laatsteVolger)
    }

    override fun onPause() {
        instellingen.prefs.unregisterOnSharedPreferenceChangeListener(laatsteVolger)
        super.onPause()
    }

    private fun serviceAan(): Boolean {
        val mijn = ComponentName(this, GebarenService::class.java)
        val aanstaand = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return aanstaand.split(':').any { ComponentName.unflattenFromString(it) == mijn }
    }

    @Composable
    private fun Scherm(aan: Boolean, laatste: Pair<Gebaar, String>?) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_naam), style = MaterialTheme.typography.headlineMedium)

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (aan) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    if (aan) {
                        Text(stringResource(R.string.status_actief), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (laatste == null) stringResource(R.string.status_nog_niets)
                            else stringResource(R.string.status_laatste, stringResource(laatste.first.naam), laatste.second),
                        )
                    } else {
                        Text(stringResource(R.string.status_uit), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.status_uit_uitleg))
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                            Text(stringResource(R.string.naar_toegankelijkheid))
                        }
                    }
                }
            }

            Gebaar.entries.forEach { g -> GebaarRij(g) }

            TrilKeuze()

            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.xiaomi_titel), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.xiaomi_uitleg),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
                        )
                    }) { Text(stringResource(R.string.app_info_openen)) }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun GebaarRij(g: Gebaar) {
        val beschikbaar = sensoren.beschikbaar(g)
        var gekozen by remember { mutableStateOf(instellingen.actie(g)) }
        var open by remember { mutableStateOf(false) }
        Column {
            Text(stringResource(g.naam), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (beschikbaar) g.uitleg else R.string.niet_beschikbaar),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            ExposedDropdownMenuBox(expanded = open, onExpandedChange = { if (beschikbaar) open = it }) {
                OutlinedTextField(
                    value = stringResource(if (beschikbaar) gekozen.naam else Actie.NIETS.naam),
                    onValueChange = {},
                    readOnly = true,
                    enabled = beschikbaar,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    Actie.entries.forEach { a ->
                        DropdownMenuItem(
                            text = { Text(stringResource(a.naam)) },
                            onClick = {
                                gekozen = a
                                instellingen.zetActie(g, a)
                                open = false
                            },
                        )
                    }
                }
            }
            if (beschikbaar && g != Gebaar.DUBBELTIK && gekozen != Actie.NIETS) {
                var stand by remember { mutableFloatStateOf(instellingen.gevoeligheid(g).toFloat()) }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.gevoeligheid, stringArrayResource(R.array.gevoeligheid_standen)[Math.round(stand) - 1]),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = stand,
                    onValueChange = { stand = it },
                    onValueChangeFinished = { instellingen.zetGevoeligheid(g, Math.round(stand)) },
                    valueRange = 1f..5f,
                    steps = 3,
                )
                if (g == Gebaar.SCHUDDEN) SchudAantal()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SchudAantal() {
        var aantal by remember { mutableIntStateOf(instellingen.schudAantal) }
        Text(stringResource(R.string.schud_aantal), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(1 to R.string.schud_1, 2 to R.string.schud_2).forEachIndexed { i, (n, tekst) ->
                SegmentedButton(
                    selected = n == aantal,
                    onClick = {
                        aantal = n
                        instellingen.schudAantal = n
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                    label = { Text(stringResource(tekst)) },
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TrilKeuze() {
        var gekozen by remember { mutableStateOf(instellingen.trilling) }
        Column {
            Text(stringResource(R.string.trilling_titel), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Trilling.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = t == gekozen,
                        onClick = {
                            gekozen = t
                            instellingen.trilling = t
                            tril(t)
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, Trilling.entries.size),
                        label = { Text(stringResource(t.naam), maxLines = 1) },
                    )
                }
            }
        }
    }
}
