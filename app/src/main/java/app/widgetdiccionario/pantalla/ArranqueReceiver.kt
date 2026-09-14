package app.widgetdiccionario.pantalla

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reactiva la escucha de pantalla tras reiniciar el dispositivo o actualizar la app. */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                PantallaService.iniciarSiCorresponde(context)
        }
    }
}
