package app.widgetdiccionario

import android.content.Context
import androidx.core.content.edit
import app.widgetdiccionario.widget.EstiloWidget

object Ajustes {
    private const val ARCHIVO = "ajustes"
    private const val CLAVE_PANTALLA = "actualizar_al_encender"
    private const val CLAVE_NOTIFICACION = "notificacion_con_palabra"
    private const val CLAVE_ULTIMA = "ultima_palabra_id"
    private const val CLAVE_MAZO_CLAVE = "mazo_clave"
    private const val CLAVE_MAZO_TOTAL = "mazo_total"
    private const val CLAVE_MAZO_POSICION = "mazo_posicion"
    private const val CLAVE_ESTILO_FONDO = "estilo_color_fondo"
    private const val CLAVE_ESTILO_TRANSPARENCIA = "estilo_transparencia"
    private const val CLAVE_ESTILO_TEXTO = "estilo_color_texto"
    private const val CLAVE_ESTILO_ESCALA = "estilo_escala_texto"

    /** Estado de la baraja en curso: [total] 0 significa que todavía no hay ninguna. */
    data class EstadoMazo(val clave: Long, val total: Int, val posicion: Int)

    fun actualizarAlApagar(context: Context): Boolean =
        prefs(context).getBoolean(CLAVE_PANTALLA, false)

    fun setActualizarAlApagar(context: Context, valor: Boolean) =
        prefs(context).edit { putBoolean(CLAVE_PANTALLA, valor) }

    /** Si es false la notificación del servicio queda mínima y sin la palabra. */
    fun mostrarNotificacion(context: Context): Boolean =
        prefs(context).getBoolean(CLAVE_NOTIFICACION, true)

    fun setMostrarNotificacion(context: Context, valor: Boolean) =
        prefs(context).edit { putBoolean(CLAVE_NOTIFICACION, valor) }

    fun ultimaPalabraId(context: Context): Int? =
        prefs(context).getInt(CLAVE_ULTIMA, -1).takeIf { it > 0 }

    fun setUltimaPalabraId(context: Context, id: Int) =
        prefs(context).edit { putInt(CLAVE_ULTIMA, id) }

    fun estadoMazo(context: Context): EstadoMazo = prefs(context).run {
        EstadoMazo(getLong(CLAVE_MAZO_CLAVE, 0L), getInt(CLAVE_MAZO_TOTAL, 0), getInt(CLAVE_MAZO_POSICION, 0))
    }

    fun setEstadoMazo(context: Context, estado: EstadoMazo) = prefs(context).edit {
        putLong(CLAVE_MAZO_CLAVE, estado.clave)
        putInt(CLAVE_MAZO_TOTAL, estado.total)
        putInt(CLAVE_MAZO_POSICION, estado.posicion)
    }

    fun estiloWidget(context: Context): EstiloWidget = prefs(context).run {
        val base = EstiloWidget.PREDETERMINADO
        EstiloWidget(
            colorFondo = if (contains(CLAVE_ESTILO_FONDO)) getInt(CLAVE_ESTILO_FONDO, 0) else null,
            transparencia = getInt(CLAVE_ESTILO_TRANSPARENCIA, base.transparencia),
            colorTexto = if (contains(CLAVE_ESTILO_TEXTO)) getInt(CLAVE_ESTILO_TEXTO, 0) else null,
            escalaTexto = getInt(CLAVE_ESTILO_ESCALA, base.escalaTexto),
        )
    }

    fun setEstiloWidget(context: Context, estilo: EstiloWidget) = prefs(context).edit {
        estilo.colorFondo?.let { putInt(CLAVE_ESTILO_FONDO, it) } ?: remove(CLAVE_ESTILO_FONDO)
        putInt(CLAVE_ESTILO_TRANSPARENCIA, estilo.transparencia)
        estilo.colorTexto?.let { putInt(CLAVE_ESTILO_TEXTO, it) } ?: remove(CLAVE_ESTILO_TEXTO)
        putInt(CLAVE_ESTILO_ESCALA, estilo.escalaTexto)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
}
