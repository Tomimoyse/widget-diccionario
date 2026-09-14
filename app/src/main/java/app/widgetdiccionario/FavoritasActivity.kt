package app.widgetdiccionario

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
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
 * Lista de palabras favoritas. Al tocar la estrella se quita la favorita, pero la fila sigue visible
 * (con la estrella vacía) hasta salir de la pantalla, así un toque accidental se deshace tocando otra vez.
 */
class FavoritasActivity : Activity() {

    private val scope = MainScope()
    private val adaptador = Adaptador()
    private lateinit var titulo: TextView
    private lateinit var vacia: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favoritas)
        titulo = findViewById(R.id.titulo_favoritas)
        vacia = findViewById(R.id.favoritas_vacia)
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
        scope.cancel()
        super.onDestroy()
    }

    private fun actualizarEncabezado() {
        val cantidad = adaptador.cantidadMarcadas()
        titulo.text = getString(R.string.favoritas_titulo_cantidad, cantidad)
        vacia.visibility = if (adaptador.count == 0) View.VISIBLE else View.GONE
    }

    private fun alternar(favorita: Favorita) = scope.launch {
        if (adaptador.estaMarcada(favorita)) {
            Favoritas.quitar(this@FavoritasActivity, favorita)
        } else {
            Favoritas.restaurar(this@FavoritasActivity, favorita)
        }
        adaptador.alternar(favorita)
        actualizarEncabezado()
        // Si es la palabra que muestra el widget, su estrella tiene que reflejar el cambio.
        ActualizadorWidget.repintar(this@FavoritasActivity)
    }

    private inner class Adaptador : BaseAdapter() {
        private var favoritas: List<Favorita> = emptyList()
        private val quitadas = mutableSetOf<String>()

        fun cargar(nuevas: List<Favorita>) {
            favoritas = nuevas
            quitadas.clear()
            notifyDataSetChanged()
        }

        fun estaMarcada(favorita: Favorita) = favorita.palabra !in quitadas

        fun alternar(favorita: Favorita) {
            if (!quitadas.remove(favorita.palabra)) quitadas.add(favorita.palabra)
            notifyDataSetChanged()
        }

        fun cantidadMarcadas() = favoritas.size - quitadas.size

        override fun getCount() = favoritas.size
        override fun getItem(position: Int) = favoritas[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val vista = convertView
                ?: LayoutInflater.from(parent.context).inflate(R.layout.item_favorita, parent, false)
            val favorita = favoritas[position]
            val marcada = estaMarcada(favorita)

            vista.findViewById<TextView>(R.id.item_palabra).text =
                listOfNotNull(favorita.palabra, favorita.categoria?.takeIf { it.isNotBlank() }).joinToString(" · ")
            vista.findViewById<TextView>(R.id.item_definicion).text = favorita.definicion
            vista.findViewById<ImageButton>(R.id.item_estrella).apply {
                setImageResource(if (marcada) R.drawable.ic_estrella_llena else R.drawable.ic_estrella_vacia)
                contentDescription = getString(if (marcada) R.string.favorita_quitar else R.string.favorita_marcar)
                setOnClickListener { alternar(favorita) }
            }
            vista.alpha = if (marcada) 1f else 0.5f
            return vista
        }
    }
}
