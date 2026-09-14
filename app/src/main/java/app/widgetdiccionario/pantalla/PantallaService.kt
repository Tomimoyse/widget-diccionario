package app.widgetdiccionario.pantalla

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.widgetdiccionario.Ajustes
import app.widgetdiccionario.widget.ActualizadorWidget
import app.widgetdiccionario.widget.lanzarAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano cuyo único trabajo es mantener registrado [receptor]:
 * ACTION_SCREEN_OFF solo se entrega a receivers registrados dinámicamente en un proceso vivo.
 * Se cambia la palabra al apagar para que ya esté pintada cuando se vuelva a encender la pantalla.
 */
class PantallaService : Service() {

    private val receptor = PantallaApagadaReceiver()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificacionPalabra.crearCanales(this)
        // startForeground debe llamarse enseguida; la palabra actual se lee de la base de datos justo después.
        ServiceCompat.startForeground(
            this,
            NotificacionPalabra.ID,
            NotificacionPalabra.construir(this, palabra = null),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        scope.launch {
            val palabra = ActualizadorWidget.palabraActual(this@PantallaService) ?: return@launch
            NotificacionPalabra.refrescar(this@PantallaService, palabra)
        }
        ContextCompat.registerReceiver(
            this,
            receptor,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACCION_REFRESCAR) {
            // Toque en el widget: además de arrancar el servicio (permitido por ser interacción del usuario)
            // muestra otra palabra, como hace el toque cuando la escucha de pantalla está desactivada.
            scope.launch { ActualizadorWidget.mostrarPalabraNueva(this@PantallaService) }
        }
        if (!Ajustes.actualizarAlApagar(this)) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        unregisterReceiver(receptor)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    class PantallaApagadaReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_OFF) return
            lanzarAsync { ActualizadorWidget.mostrarPalabraNueva(context) }
        }
    }

    companion object {
        private const val TAG = "PantallaService"
        const val ACCION_REFRESCAR = "app.widgetdiccionario.REFRESCAR_Y_ESCUCHAR"

        /** Hace falta si está activado y hay algo que actualizar: un widget o la notificación con palabra. */
        fun esNecesario(context: Context): Boolean =
            Ajustes.actualizarAlApagar(context) &&
                (Ajustes.mostrarNotificacion(context) || ActualizadorWidget.idsWidgets(context).isNotEmpty())

        /** Arranca el servicio si [esNecesario]. */
        fun iniciarSiCorresponde(context: Context) {
            if (!esNecesario(context)) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, PantallaService::class.java))
            } catch (e: IllegalStateException) {
                // Android 12+ puede rechazar el arranque desde segundo plano (p. ej. desde onEnabled).
                // Se reintentará al abrir la app o al reiniciar el dispositivo.
                Log.w(TAG, "No se pudo iniciar el servicio desde segundo plano", e)
            }
        }

        fun detener(context: Context) {
            context.stopService(Intent(context, PantallaService::class.java))
        }
    }
}
