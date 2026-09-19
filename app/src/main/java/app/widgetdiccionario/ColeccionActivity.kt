package app.widgetdiccionario

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import app.widgetdiccionario.data.Favorita
import app.widgetdiccionario.data.Favoritas
import app.widgetdiccionario.data.OrdenAlfabetico
import app.widgetdiccionario.widget.ActualizadorWidget
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * La colección: las palabras guardadas, en orden alfabético y agrupadas por letra inicial.
 * Quitar una la saca de la lista al instante; durante unos segundos una barra permite deshacerlo.
 */
class ColeccionActivity : Activity() {

    private val scope = MainScope()
    private val adaptador = Adaptador()
    private val manejador = Handler(Looper.getMainLooper())
    private val ocultarBarra = Runnable { barraDeshacer.visibility = View.GONE }

    private lateinit var cantidad: TextView
    private lateinit var vacia: TextView
    private lateinit var barraDeshacer: View
    private lateinit var textoDeshacer: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coleccion)
        cantidad = findViewById(R.id.cantidad_coleccion)
        vacia = findViewById(R.id.coleccion_vacia)
        barraDeshacer = findViewById(R.id.barra_deshacer)
        textoDeshacer = findViewById(R.id.texto_deshacer)
        findViewById<ListView>(R.id.lista_coleccion).adapter = adaptador
    }

    override fun onResume() {
        super.onResume()
        scope.launch {
            adaptador.cargar(Favoritas.todas(this@ColeccionActivity))
            actualizarEncabezado()
        }
    }

    override fun onDestroy() {
        manejador.removeCallbacks(ocultarBarra)
        scope.cancel()
        super.onDestroy()
    }

    private fun actualizarEncabezado() {
        val total = adaptador.cantidadDePalabras()
        cantidad.text = resources.getQuantityString(R.plurals.coleccion_cantidad, total, total)
        vacia.visibility = if (total == 0) View.VISIBLE else View.GONE
    }

    private fun quitar(favorita: Favorita) = scope.launch {
        // Se saca primero de la lista: un segundo toque rápido sobre la misma fila no encuentra nada.
        if (!adaptador.sacarDeLaLista(favorita)) return@launch
        Favoritas.quitar(this@ColeccionActivity, favorita)
        actualizarEncabezado()
        mostrarDeshacer(favorita)
        // Si es la palabra que muestra el widget, su estrella tiene que reflejar el cambio.
        ActualizadorWidget.repintar(this@ColeccionActivity)
    }

    private fun mostrarDeshacer(favorita: Favorita) {
        textoDeshacer.text = getString(R.string.coleccion_quitada, favorita.palabra)
        findViewById<Button>(R.id.boton_deshacer).setOnClickListener {
            manejador.removeCallbacks(ocultarBarra)
            barraDeshacer.visibility = View.GONE
            scope.launch {
                Favoritas.restaurar(this@ColeccionActivity, favorita)
                adaptador.volverALaLista(favorita)
                actualizarEncabezado()
                ActualizadorWidget.repintar(this@ColeccionActivity)
            }
        }
        barraDeshacer.visibility = View.VISIBLE
        manejador.removeCallbacks(ocultarBarra)
        manejador.postDelayed(ocultarBarra, DURACION_DESHACER_MS)
    }

    /** Cada fila de la lista: o la letra que abre un grupo, o una palabra. */
    private sealed interface Fila {
        data class Letra(val letra: String) : Fila
        data class Palabra(val favorita: Favorita) : Fila
    }

    private inner class Adaptador : BaseAdapter() {
        private val palabras = mutableListOf<Favorita>()
        private var filas = emptyList<Fila>()

        fun cargar(nuevas: List<Favorita>) {
            palabras.clear()
            palabras.addAll(nuevas)
            rehacerFilas()
        }

        /** Solo toca la lista (no la base de datos). Devuelve false si ya no estaba. */
        fun sacarDeLaLista(favorita: Favorita): Boolean {
            if (!palabras.remove(favorita)) return false
            rehacerFilas()
            return true
        }

        fun volverALaLista(favorita: Favorita) {
            palabras.add(favorita)
            rehacerFilas()
        }

        fun cantidadDePalabras() = palabras.size

        private fun rehacerFilas() {
            palabras.sortWith(OrdenAlfabetico.comparadorDePalabras)
            val nuevas = mutableListOf<Fila>()
            var letraActual: String? = null
            palabras.forEach { favorita ->
                val letra = OrdenAlfabetico.letraInicial(favorita.palabra)
                if (letra != letraActual) {
                    nuevas.add(Fila.Letra(letra))
                    letraActual = letra
                }
                nuevas.add(Fila.Palabra(favorita))
            }
            filas = nuevas
            notifyDataSetChanged()
        }

        override fun getCount() = filas.size
        override fun getItem(position: Int) = filas[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (filas[position] is Fila.Letra) 0 else 1

        // Las letras son separadores: no reaccionan al toque.
        override fun areAllItemsEnabled() = false
        override fun isEnabled(position: Int) = filas[position] is Fila.Palabra

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val inflador = LayoutInflater.from(parent.context)
            return when (val fila = filas[position]) {
                is Fila.Letra -> {
                    val vista = convertView ?: inflador.inflate(R.layout.item_coleccion_letra, parent, false)
                    vista.findViewById<TextView>(R.id.letra).text = fila.letra
                    vista
                }

                is Fila.Palabra -> {
                    val vista = convertView ?: inflador.inflate(R.layout.item_coleccion, parent, false)
                    val favorita = fila.favorita
                    vista.findViewById<TextView>(R.id.item_palabra).text = favorita.palabra
                    vista.findViewById<TextView>(R.id.item_categoria).text =
                        favorita.categoria?.takeIf { it.isNotBlank() }?.let { "· $it" }.orEmpty()
                    vista.findViewById<TextView>(R.id.item_definicion).text = favorita.definicion
                    vista.findViewById<ImageButton>(R.id.item_estrella).setOnClickListener {
                        // Explícito: dentro del adaptador, un "quitar" a secas podría resolverse a un método suyo.
                        this@ColeccionActivity.quitar(favorita)
                    }
                    vista
                }
            }
        }
    }

    private companion object {
        const val DURACION_DESHACER_MS = 4_000L
    }
}
