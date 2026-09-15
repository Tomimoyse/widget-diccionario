package app.widgetdiccionario

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Html
import android.view.View
import android.widget.TextView
import androidx.core.net.toUri
import app.widgetdiccionario.pantalla.NotificacionPalabra
import app.widgetdiccionario.widget.ActualizadorWidget

/**
 * Para celulares que no son Samsung: la palabra llega a la pantalla de bloqueo con la notificación.
 * Lista lo que tiene que estar activado, con el estado real de lo que se puede comprobar.
 */
class OtraMarcaActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otra_marca)
        findViewById<View>(R.id.boton_ajustes_app).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
        }
    }

    /** Se recalcula al volver, por ejemplo después de cambiar algo en los ajustes del sistema. */
    override fun onResume() {
        super.onResume()
        fun estado(ok: Boolean) = getString(if (ok) R.string.ayuda_ok else R.string.ayuda_revisar)
        val info = getString(R.string.ayuda_info)
        val bateriaSinRestricciones = getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(packageName)

        val puntos = listOf(
            getString(R.string.ayuda_pantalla, estado(Ajustes.actualizarAlApagar(this))),
            getString(R.string.ayuda_permiso, estado(NotificacionPalabra.tienePermiso(this))),
            getString(R.string.ayuda_notificacion, estado(Ajustes.mostrarNotificacion(this))),
            getString(R.string.ayuda_bateria, estado(bateriaSinRestricciones)),
            getString(R.string.ayuda_bloqueo, info),
            getString(R.string.ayuda_widget, estado(ActualizadorWidget.idsWidgets(this).isNotEmpty())),
            getString(R.string.ayuda_reactivar, info),
        )
        findViewById<TextView>(R.id.requisitos).text =
            Html.fromHtml(puntos.joinToString("<br><br>"), Html.FROM_HTML_MODE_COMPACT)
    }
}
