package app.widgetdiccionario

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import app.widgetdiccionario.data.Favoritas
import app.widgetdiccionario.pantalla.PantallaService
import app.widgetdiccionario.widget.ActualizadorWidget
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Pantalla de inicio: la palabra del momento y los accesos a la colección, a compartir, a
 * personalizar y al funcionamiento de la app.
 */
class MainActivity : Activity() {

    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val vistaPalabra = findViewById<TextView>(R.id.vista_palabra)
        val vistaCategoria = findViewById<TextView>(R.id.vista_categoria)
        val vistaDefinicion = findViewById<TextView>(R.id.vista_definicion)

        fun mostrarOtra() = scope.launch {
            val palabra = ActualizadorWidget.siguientePalabra(this@MainActivity) ?: return@launch
            vistaPalabra.text = palabra.palabra
            vistaCategoria.text = palabra.categoria?.takeIf { it.isNotBlank() }?.let { "· $it" }.orEmpty()
            vistaDefinicion.text = palabra.definicion
            ActualizadorWidget.mostrar(this@MainActivity, palabra)
        }

        findViewById<Button>(R.id.boton_nueva).setOnClickListener { mostrarOtra() }
        findViewById<Button>(R.id.boton_coleccion).setOnClickListener {
            startActivity(Intent(this, ColeccionActivity::class.java))
        }
        findViewById<View>(R.id.acceso_compartir).setOnClickListener {
            startActivity(Intent(this, IntercambioActivity::class.java))
        }
        findViewById<View>(R.id.acceso_personalizar).setOnClickListener {
            startActivity(Intent(this, PersonalizarActivity::class.java))
        }
        findViewById<View>(R.id.acceso_funcionamiento).setOnClickListener {
            startActivity(Intent(this, FuncionamientoActivity::class.java))
        }
        mostrarOtra()
    }

    override fun onResume() {
        super.onResume()
        scope.launch {
            val cantidad = Favoritas.contar(this@MainActivity)
            findViewById<Button>(R.id.boton_coleccion).text =
                if (cantidad == 0) getString(R.string.main_boton_coleccion)
                else getString(R.string.main_boton_coleccion_cantidad, cantidad)
        }
        // Desde una Activity visible el arranque en primer plano siempre está permitido.
        PantallaService.iniciarSiCorresponde(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
