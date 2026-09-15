package app.widgetdiccionario

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.graphics.toColorInt
import app.widgetdiccionario.data.Favoritas
import app.widgetdiccionario.widget.ActualizadorWidget
import app.widgetdiccionario.widget.EstiloWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Personalización del widget con vista previa en vivo. Los cambios solo se guardan y se aplican al widget
 * al tocar «Confirmar»; si se sale antes, se descartan.
 */
class EstiloWidgetActivity : Activity() {

    private val scope = MainScope()
    private lateinit var estilo: EstiloWidget
    private lateinit var guardado: EstiloWidget
    private lateinit var vistaPrevia: View
    private var favoritaEnVistaPrevia = false

    private val coloresFondo = listOf(
        Muestra(null, R.string.color_automatico),
        Muestra("#000000".toColorInt(), R.string.color_negro),
        Muestra("#2B2B2B".toColorInt(), R.string.color_carbon),
        Muestra("#FFFFFF".toColorInt(), R.string.color_blanco),
        Muestra("#ECE5DC".toColorInt(), R.string.color_papel),
        Muestra("#C8B79E".toColorInt(), R.string.color_arena),
        Muestra("#1F2A44".toColorInt(), R.string.color_azul_noche),
        Muestra("#24382B".toColorInt(), R.string.color_verde_bosque),
        Muestra("#4A1F24".toColorInt(), R.string.color_borgona),
    )

    private val coloresTexto = listOf(
        Muestra(null, R.string.color_automatico),
        Muestra("#FFFFFF".toColorInt(), R.string.color_blanco),
        Muestra("#111111".toColorInt(), R.string.color_negro),
        Muestra("#ECE5DC".toColorInt(), R.string.color_papel),
        Muestra("#BDBDBD".toColorInt(), R.string.color_gris),
        Muestra("#E0B45C".toColorInt(), R.string.color_dorado),
        Muestra("#A9C7E8".toColorInt(), R.string.color_celeste),
        Muestra("#E8B4B8".toColorInt(), R.string.color_rosa),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_estilo_widget)
        guardado = Ajustes.estiloWidget(this)
        estilo = guardado

        val contenedor = findViewById<FrameLayout>(R.id.vista_previa)
        vistaPrevia = LayoutInflater.from(this).inflate(R.layout.widget_palabra, contenedor, false)
        contenedor.addView(vistaPrevia)
        scope.launch {
            ActualizadorWidget.palabraActual(this@EstiloWidgetActivity)?.let { palabra ->
                vistaPrevia.findViewById<TextView>(R.id.palabra).text = palabra.palabra
                vistaPrevia.findViewById<TextView>(R.id.categoria).text = palabra.categoria.orEmpty()
                vistaPrevia.findViewById<TextView>(R.id.definicion).text = palabra.definicion
                favoritaEnVistaPrevia = Favoritas.esFavorita(this@EstiloWidgetActivity, palabra)
                refrescar()
            }
        }

        findViewById<SeekBar>(R.id.barra_transparencia).apply {
            progress = estilo.transparencia
            setOnSeekBarChangeListener(alMover { valor -> estilo = estilo.copy(transparencia = valor) })
        }
        findViewById<SeekBar>(R.id.barra_tamano).apply {
            max = (EstiloWidget.ESCALA_MAXIMA - EstiloWidget.ESCALA_MINIMA) / PASO_ESCALA
            progress = (estilo.escalaTexto - EstiloWidget.ESCALA_MINIMA) / PASO_ESCALA
            setOnSeekBarChangeListener(alMover { valor ->
                estilo = estilo.copy(escalaTexto = EstiloWidget.ESCALA_MINIMA + valor * PASO_ESCALA)
            })
        }
        findViewById<View>(R.id.boton_restablecer).setOnClickListener {
            estilo = EstiloWidget.PREDETERMINADO
            findViewById<SeekBar>(R.id.barra_transparencia).progress = estilo.transparencia
            findViewById<SeekBar>(R.id.barra_tamano).progress =
                (estilo.escalaTexto - EstiloWidget.ESCALA_MINIMA) / PASO_ESCALA
            refrescar()
        }
        findViewById<View>(R.id.boton_confirmar).setOnClickListener { confirmar() }
        refrescar()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Solo actualiza la vista previa: el widget cambia al confirmar. */
    private fun alMover(alCambiar: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(barra: SeekBar, valor: Int, delUsuario: Boolean) {
            if (!delUsuario) return
            alCambiar(valor)
            refrescar()
        }

        override fun onStartTrackingTouch(barra: SeekBar) = Unit
        override fun onStopTrackingTouch(barra: SeekBar) = Unit
    }

    private fun confirmar() {
        Ajustes.setEstiloWidget(this, estilo)
        guardado = estilo
        // El repintado no depende de esta pantalla, que se cierra enseguida.
        val app = applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { ActualizadorWidget.repintar(app) }
        Toast.makeText(this, R.string.estilo_aplicado, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun refrescar() {
        estilo.aplicar(vistaPrevia, favoritaEnVistaPrevia)
        findViewById<TextView>(R.id.valor_transparencia).text = getString(R.string.estilo_porcentaje, estilo.transparencia)
        findViewById<TextView>(R.id.valor_tamano).text = getString(R.string.estilo_porcentaje, estilo.escalaTexto)
        pintarMuestras(findViewById(R.id.colores_fondo), coloresFondo, estilo.colorFondo) { color ->
            estilo = estilo.copy(colorFondo = color)
            refrescar()
        }
        pintarMuestras(findViewById(R.id.colores_texto), coloresTexto, estilo.colorTexto) { color ->
            estilo = estilo.copy(colorTexto = color)
            refrescar()
        }
        val hayCambios = estilo != guardado
        findViewById<View>(R.id.boton_confirmar).apply {
            isEnabled = hayCambios
            alpha = if (hayCambios) 1f else 0.4f
        }
        findViewById<View>(R.id.estilo_pendiente).visibility = if (hayCambios) View.VISIBLE else View.INVISIBLE
    }

    private fun pintarMuestras(fila: LinearLayout, muestras: List<Muestra>, elegido: Int?, alElegir: (Int?) -> Unit) {
        fila.removeAllViews()
        val tamano = dp(42)
        muestras.forEachIndexed { indice, muestra ->
            val seleccionada = muestra.color == elegido
            val circulo = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(tamano, tamano).apply {
                    if (indice > 0) marginStart = dp(12)
                }
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(muestra.color ?: getColor(R.color.papel_hondo))
                    setStroke(
                        if (seleccionada) dp(3) else dp(1),
                        getColor(if (seleccionada) R.color.tinta else R.color.linea),
                    )
                }
                if (muestra.color == null) {
                    text = getString(R.string.color_automatico_corto)
                    setTextColor(getColor(R.color.tinta))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                }
                contentDescription = getString(muestra.nombre) +
                    if (seleccionada) getString(R.string.color_seleccionado) else ""
                setOnClickListener { alElegir(muestra.color) }
            }
            fila.addView(circulo)
        }
    }

    private fun dp(valor: Int) = (valor * resources.displayMetrics.density).toInt()

    private data class Muestra(val color: Int?, @StringRes val nombre: Int)

    private companion object {
        const val PASO_ESCALA = 5
    }
}
