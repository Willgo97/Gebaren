package nl.gebaren.app

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent

class Acties(private val service: AccessibilityService) {
    private val camera = service.getSystemService(CameraManager::class.java)
    private var flitsId: String? = null
    private var zaklampAan = false

    private val zaklampVolger = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, aan: Boolean) {
            if (id == flitsId) zaklampAan = aan
        }
    }

    init {
        flitsId = runCatching {
            camera.cameraIdList.firstOrNull { id ->
                val k = camera.getCameraCharacteristics(id)
                k.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    k.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        }.getOrNull()
        camera.registerTorchCallback(zaklampVolger, Handler(Looper.getMainLooper()))
    }

    fun stop() = camera.unregisterTorchCallback(zaklampVolger)

    fun voerUit(a: Actie) {
        runCatching {
            when (a) {
                Actie.NIETS -> Unit
                Actie.ZAKLAMP -> flitsId?.let { camera.setTorchMode(it, !zaklampAan) }
                Actie.CAMERA -> openCamera()
                Actie.MEDIA -> mediaToets()
                Actie.VERGRENDELEN -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                Actie.SCHERMAFBEELDING -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            }
        }.onFailure { Log.w("Gebaren", "actie $a mislukt", it) }
    }

    private fun openCamera() {
        val pm = service.getSystemService(PowerManager::class.java)
        if (!pm.isInteractive) {
            @Suppress("DEPRECATION")
            pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "gebaren:camera",
            ).acquire(1_000)
        }
        val vergrendeld = service.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val actie = if (vergrendeld) MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE
        else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        service.startActivity(eenCamera(Intent(actie)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // De Xiaomi-camera heeft twee activiteiten voor de beveiligde intent, en een keuzemenu kan niet over het slotscherm.
    private fun eenCamera(intent: Intent): Intent {
        val pm = service.packageManager
        val kandidaten = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (kandidaten.size <= 1) return intent
        val standaardApp = pm.resolveActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), 0)
            ?.activityInfo?.packageName
        val keuze = kandidaten.firstOrNull { it.activityInfo.packageName == standaardApp } ?: kandidaten.first()
        return intent.setClassName(keuze.activityInfo.packageName, keuze.activityInfo.name)
    }

    private fun mediaToets() {
        val audio = service.getSystemService(AudioManager::class.java)
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }
}
