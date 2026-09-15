package app.widgetdiccionario.widget

import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import app.widgetdiccionario.R

/**
 * Apariencia configurable del widget. Un color null significa "automático": se respetan los colores
 * del sistema definidos en los recursos (claro/oscuro y Material You), sin tocarlos.
 */
data class EstiloWidget(
    val colorFondo: Int?,
    /** 0 = fondo opaco, 100 = fondo invisible. */
    val transparencia: Int,
    val colorTexto: Int?,
    /** Porcentaje sobre los tamaños base. */
    val escalaTexto: Int,
) {
    private val alfaFondo: Float get() = (100 - transparencia) / 100f
    private fun tamano(base: Float) = base * escalaTexto / 100f

    /** Aplica el estilo a las vistas remotas del widget. */
    fun aplicar(vistas: RemoteViews, favorita: Boolean) {
        vistas.setFloat(R.id.fondo, "setAlpha", alfaFondo)
        colorFondo?.let { vistas.setInt(R.id.fondo, "setColorFilter", it) }

        vistas.setTextViewTextSize(R.id.palabra, TypedValue.COMPLEX_UNIT_SP, tamano(TAMANO_PALABRA))
        vistas.setTextViewTextSize(R.id.categoria, TypedValue.COMPLEX_UNIT_SP, tamano(TAMANO_CATEGORIA))
        vistas.setTextViewTextSize(R.id.definicion, TypedValue.COMPLEX_UNIT_SP, tamano(TAMANO_DEFINICION))
        colorTexto?.let { color ->
            vistas.setTextColor(R.id.palabra, color)
            vistas.setTextColor(R.id.definicion, color)
            vistas.setTextColor(R.id.categoria, atenuado(color))
            // La estrella llena conserva su dorado; la vacía acompaña al texto.
            if (!favorita) vistas.setInt(R.id.estrella, "setColorFilter", atenuado(color))
        }
    }

    /** Mismo estilo sobre vistas normales, para la vista previa de la app. */
    fun aplicar(raiz: View, favorita: Boolean) {
        raiz.findViewById<ImageView>(R.id.fondo).apply {
            alpha = alfaFondo
            if (colorFondo != null) setColorFilter(colorFondo) else clearColorFilter()
        }
        val palabra = raiz.findViewById<TextView>(R.id.palabra)
        val categoria = raiz.findViewById<TextView>(R.id.categoria)
        val definicion = raiz.findViewById<TextView>(R.id.definicion)
        palabra.setTextSize(TypedValue.COMPLEX_UNIT_SP, tamano(TAMANO_PALABRA))
        categoria.setTextSize(TypedValue.COMPLEX_UNIT_SP, tamano(TAMANO_CATEGORIA))
        definicion.setTextSize(TypedValue.COMPLEX_UNIT_SP, tamano(TAMANO_DEFINICION))

        val contexto = raiz.context
        val texto = colorTexto ?: contexto.getColor(R.color.widget_texto)
        val secundario = colorTexto?.let(::atenuado) ?: contexto.getColor(R.color.widget_texto_secundario)
        palabra.setTextColor(texto)
        definicion.setTextColor(texto)
        categoria.setTextColor(secundario)
        raiz.findViewById<ImageView>(R.id.estrella).apply {
            setImageResource(if (favorita) R.drawable.ic_estrella_llena else R.drawable.ic_estrella_vacia)
            if (!favorita && colorTexto != null) setColorFilter(atenuado(colorTexto)) else clearColorFilter()
        }
    }

    companion object {
        const val TAMANO_PALABRA = 18f
        const val TAMANO_CATEGORIA = 12f
        const val TAMANO_DEFINICION = 13f

        const val ESCALA_MINIMA = 70
        const val ESCALA_MAXIMA = 160

        val PREDETERMINADO = EstiloWidget(colorFondo = null, transparencia = 45, colorTexto = null, escalaTexto = 100)

        private fun atenuado(color: Int) = Color.argb(0xC0, Color.red(color), Color.green(color), Color.blue(color))
    }
}
