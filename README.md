# Gebaren

Moto Actions voor elke Android-telefoon: schudden, draaien en dubbeltikken op de achterkant, elk met een eigen actie. Dat is alles wat de app doet.

**Download:** <https://github.com/Willgo97/Gebaren/releases/latest/download/Gebaren.apk>

| Gebaar | Standaard | Kan ook |
|---|---|---|
| Schudden (1× of 2×) | Zaklamp | Camera, muziek, vergrendelen, schermafbeelding |
| Draaien (2× de pols) | Camera | idem |
| Dubbeltik achterkant | — | idem, alleen op Xiaomi |

## Aanzetten

1. Installeer de APK en open de app.
2. *Naar Toegankelijkheid* → *Gedownloade apps* → *Gebaren* aan. Staat hij grijs: App-info → ⋮ → *Beperkte instellingen toestaan*.
3. Xiaomi/HyperOS: Autostart aan, Accubesparing op *Geen beperkingen*.

## Zuinig

Accelerometer en gyroscoop worden in de sensor-hub gebufferd terwijl de processor slaapt. Pas als een wake-up-sensor afgaat (kantelen, oppakken) haalt de app de laatste anderhalve seconde op en kijkt of het een gebaar was. Geen internet, geen achtergrondtaken.

## Bouwen

`./gradlew assembleRelease` met JDK 17. Voor een getekende release: `app/keystore.properties` naar het voorbeeld ernaast.
