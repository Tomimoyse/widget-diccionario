package app.widgetdiccionario.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.widgetdiccionario.Ajustes
import app.widgetdiccionario.R
import app.widgetdiccionario.data.DiccionarioDatabase
import app.widgetdiccionario.data.Mazo
import app.widgetdiccionario.data.Palabra
import app.widgetdiccionario.pantalla.NotificacionPalabra
import app.widgetdiccionario.pantalla.PantallaService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

object ActualizadorWidget {

    /** Serializa los avances del mazo (apagado de pantalla, toque y app pueden coincidir). */
    private val mutexMazo = Mutex()

    fun idsWidgets(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, PalabraWidgetProvider::class.java))

    /** Elige una palabra nueva y la muestra en los widgets (o solo en [ids]) y en la notificación. */
    suspend fun mostrarPalabraNueva(context: Context, ids: IntArray = idsWidgets(context)) {
        val hayNotificacion = Ajustes.actualizarAlApagar(context) && Ajustes.mostrarNotificacion(context)
        if (ids.isEmpty() && !hayNotificacion) return
        val palabra = siguientePalabra(context) ?: return
        mostrar(context, palabra, ids)
    }

    /**
     * Saca la siguiente carta del mazo: no repite ninguna palabra hasta haberlas mostrado todas.
     * Al agotarse (o si cambia el tamaño del diccionario) se baraja un mazo nuevo.
     */
    suspend fun siguientePalabra(context: Context): Palabra? = mutexMazo.withLock {
        val dao = DiccionarioDatabase.get(context).palabraDao()
        val total = dao.maxId() ?: return null
        val ultima = Ajustes.ultimaPalabraId(context)
        var estado = Ajustes.estadoMazo(context)

        if (estado.total != total || estado.posicion >= total) {
            estado = barajar(total, evitarPrimera = ultima)
        }
        val id = Mazo(total, estado.clave).carta(estado.posicion)
        Ajustes.setEstadoMazo(context, estado.copy(posicion = estado.posicion + 1))
        dao.porId(id)
    }

    /** Mazo nuevo cuya primera carta no sea [evitarPrimera], para no repetir justo en el cambio de mazo. */
    private fun barajar(total: Int, evitarPrimera: Int?): Ajustes.EstadoMazo {
        var clave: Long
        do clave = Random.nextLong() while (total > 1 && Mazo(total, clave).carta(0) == evitarPrimera)
        return Ajustes.EstadoMazo(clave, total, posicion = 0)
    }

    fun mostrar(context: Context, palabra: Palabra, ids: IntArray = idsWidgets(context)) {
        Ajustes.setUltimaPalabraId(context, palabra.id)
        if (ids.isNotEmpty()) {
            AppWidgetManager.getInstance(context).updateAppWidget(ids, vistas(context, palabra))
        }
        NotificacionPalabra.actualizar(context, palabra)
    }

    /** La última palabra mostrada, o null si todavía no se mostró ninguna. */
    suspend fun palabraActual(context: Context): Palabra? =
        Ajustes.ultimaPalabraId(context)?.let { DiccionarioDatabase.get(context).palabraDao().porId(it) }

    private fun vistas(context: Context, palabra: Palabra) =
        RemoteViews(context.packageName, R.layout.widget_palabra).apply {
            setTextViewText(R.id.palabra, palabra.palabra)
            setTextViewText(R.id.definicion, palabra.definicion)
            if (palabra.categoria.isNullOrBlank()) {
                setViewVisibility(R.id.categoria, View.GONE)
            } else {
                setViewVisibility(R.id.categoria, View.VISIBLE)
                setTextViewText(R.id.categoria, palabra.categoria)
            }
            setOnClickPendingIntent(android.R.id.background, intentRefrescar(context))
        }

    /** Vuelve a pintar la palabra actual (p. ej. para que el toque use el PendingIntent adecuado). */
    suspend fun repintar(context: Context) {
        val palabra = palabraActual(context) ?: siguientePalabra(context) ?: return
        mostrar(context, palabra)
    }

    /**
     * Con la escucha de pantalla activada, el toque arranca [PantallaService] directamente: Android no deja
     * iniciarlo desde segundo plano (p. ej. al colocar el widget), pero sí desde una interacción del usuario.
     */
    private fun intentRefrescar(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Ajustes.actualizarAlApagar(context)) {
            PendingIntent.getForegroundService(
                context,
                0,
                Intent(context, PantallaService::class.java).setAction(PantallaService.ACCION_REFRESCAR),
                flags,
            )
        } else {
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, PalabraWidgetProvider::class.java).setAction(PalabraWidgetProvider.ACCION_REFRESCAR),
                flags,
            )
        }
    }
}

/** Ejecuta [bloque] fuera del hilo principal manteniendo vivo el broadcast hasta que termine. */
fun BroadcastReceiver.lanzarAsync(bloque: suspend () -> Unit) {
    val pendiente = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            bloque()
        } finally {
            pendiente.finish()
        }
    }
}
