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
import app.widgetdiccionario.widget.ActualizadorWidget
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lista de palabras favoritas. Quitar una la saca de la lista al instante; durante unos segundos una
 * barra permite deshacerlo.
 */
class FavoritasActivity : Activity() {

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
        setContentView(R.layout.activity_favoritas)
        cantidad = findViewById(R.id.cantidad_favoritas)
        vacia = findViewById(R.id.favoritas_vacia)
        barraDeshacer = findViewById(R.id.barra_deshacer)
        textoDeshacer = findViewById(R.id.texto_deshacer)
        findViewById<ListView>(R.id.lista_favoritas).adapter = adaptador
    }

    override fun onResume() {
        super.onResume()
        scope.launch {
            adaptador.cargar(Favoritas.todas(this@FavoritasActivity))
            actualizarEncabezado()
        }
    }

    override fun onDestroy() {
        manejador.removeCallbacks(ocultarBarra)
        scope.cancel()
        super.onDestroy()
    }

    private fun actualizarEncabezado() {
        val total = adaptador.count
        cantidad.text = resources.getQuantityString(R.plurals.favoritas_cantidad, total, total)
        vacia.visibility = if (total == 0) View.VISIBLE else View.GONE
    }

    private fun quitar(favorita: Favorita) = scope.launch {
        // Se saca primero de la lista: un segundo toque rápido sobre la misma fila no encuentra nada.
        val posicion = adaptador.sacarDeLaLista(favorita)
        if (posicion < 0) return@launch
        Favoritas.quitar(this@FavoritasActivity, favorita)
        actualizarEncabezado()
        mostrarDeshacer(favorita, posicion)
        // Si es la palabra que muestra el widget, su estrella tiene que reflejar el cambio.
        ActualizadorWidget.repintar(this@FavoritasActivity)
    }

    private fun mostrarDeshacer(favorita: Favorita, posicion: Int) {
        textoDeshacer.text = getString(R.string.favoritas_quitada, favorita.palabra)
        findViewById<Button>(R.id.boton_deshacer).setOnClickListener {
            manejador.removeCallbacks(ocultarBarra)
            barraDeshacer.visibility = View.GONE
            scope.launch {
                Favoritas.restaurar(this@FavoritasActivity, favorita)
                adaptador.volverALaLista(posicion, favorita)
                actualizarEncabezado()
                ActualizadorWidget.repintar(this@FavoritasActivity)
            }
        }
        barraDeshacer.visibility = View.VISIBLE
        manejador.removeCallbacks(ocultarBarra)
        manejador.postDelayed(ocultarBarra, DURACION_DESHACER_MS)
    }

    private inner class Adaptador : BaseAdapter() {
        private val favoritas = mutableListOf<Favorita>()

        fun cargar(nuevas: List<Favorita>) {
            favoritas.clear()
            favoritas.addAll(nuevas)
            notifyDataSetChanged()
        }

        /** Solo toca la lista (no la base de datos). Devuelve la posición que ocupaba, o -1 si ya no estaba. */
        fun sacarDeLaLista(favorita: Favorita): Int {
            val posicion = favoritas.indexOf(favorita)
            if (posicion >= 0) {
                favoritas.removeAt(posicion)
                notifyDataSetChanged()
            }
            return posicion
        }

        fun volverALaLista(posicion: Int, favorita: Favorita) {
            favoritas.add(posicion.coerceAtMost(favoritas.size), favorita)
            notifyDataSetChanged()
        }

        override fun getCount() = favoritas.size
        override fun getItem(position: Int) = favoritas[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val vista = convertView
                ?: LayoutInflater.from(parent.context).inflate(R.layout.item_favorita, parent, false)
            val favorita = favoritas[position]

            vista.findViewById<TextView>(R.id.item_numero).text = getString(R.string.numero_orden, position + 1)
            vista.findViewById<TextView>(R.id.item_palabra).text = favorita.palabra
            vista.findViewById<TextView>(R.id.item_categoria).text =
                favorita.categoria?.takeIf { it.isNotBlank() }?.let { "· $it" }.orEmpty()
            vista.findViewById<TextView>(R.id.item_definicion).text = favorita.definicion
            vista.findViewById<ImageButton>(R.id.item_estrella).setOnClickListener {
                // Explícito: dentro del adaptador, un "quitar" a secas podría resolverse a un método suyo.
                this@FavoritasActivity.quitar(favorita)
            }
            return vista
        }
    }

    private companion object {
        const val DURACION_DESHACER_MS = 4_000L
    }
}
