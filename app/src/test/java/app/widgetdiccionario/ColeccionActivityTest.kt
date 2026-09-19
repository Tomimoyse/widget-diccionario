package app.widgetdiccionario

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.widgetdiccionario.data.Favorita
import app.widgetdiccionario.data.FavoritasDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** La colección: orden alfabético, grupos por letra y quitar palabras (con deshacer). */
@RunWith(AndroidJUnit4::class)
class ColeccionActivityTest {

    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private val dao get() = FavoritasDatabase.get(contexto).favoritaDao()

    @Before
    fun preparar() = runBlocking {
        FavoritasDatabase.cerrarParaPruebas()
        dao.todas().forEach { dao.quitar(it.palabra) }
        guardar("anemoia", 2)
        guardar("baldaquino", 1)
    }

    private suspend fun guardar(palabra: String, agregadaEn: Long) =
        dao.guardar(Favorita(palabra, "f.", "Definición de $palabra.", agregadaEn))

    @Test
    fun quitarUnaPalabra_noReapareceAlVolverAAbrirLaColeccion() {
        ActivityScenario.launch(ColeccionActivity::class.java).use { escenario ->
            escenario.onActivity { actividad ->
                val lista = actividad.findViewById<ListView>(R.id.lista_coleccion)
                esperarHasta { palabras(lista) == listOf("anemoia", "baldaquino") }

                tocarEstrellaDe("anemoia", lista)
                esperarHasta { palabras(lista) == listOf("baldaquino") }
            }
            // El usuario vuelve al menú principal enseguida.
        }

        ActivityScenario.launch(ColeccionActivity::class.java).use { escenario ->
            escenario.onActivity { actividad ->
                val lista = actividad.findViewById<ListView>(R.id.lista_coleccion)
                esperarHasta { lista.adapter.count > 0 }
                esperarUnPoco()
                assertEquals(listOf("baldaquino"), palabras(lista))
            }
        }
        assertEquals(listOf("baldaquino"), runBlocking { dao.todas().map { it.palabra } })
    }

    @Test
    fun deshacer_devuelveLaPalabraALaColeccionYALaBase() {
        ActivityScenario.launch(ColeccionActivity::class.java).use { escenario ->
            escenario.onActivity { actividad ->
                val lista = actividad.findViewById<ListView>(R.id.lista_coleccion)
                val barra = actividad.findViewById<View>(R.id.barra_deshacer)
                esperarHasta { palabras(lista).size == 2 }

                tocarEstrellaDe("anemoia", lista)
                esperarHasta { barra.visibility == View.VISIBLE }
                assertEquals(listOf("baldaquino"), runBlocking { dao.todas().map { it.palabra } })

                actividad.findViewById<View>(R.id.boton_deshacer).performClick()
                esperarHasta { palabras(lista).size == 2 }
                esperarUnPoco()
            }
        }
        assertEquals(
            listOf("anemoia", "baldaquino"),
            runBlocking { dao.todas().map { it.palabra }.sorted() },
        )
    }

    @Test
    fun lasPalabrasSeOrdenanEnEspanolYSeAgrupanPorLetra() {
        runBlocking {
            dao.todas().forEach { dao.quitar(it.palabra) }
            listOf("ñandú", "azul", "árbol", "ámbar", "1979", "baldaquino").forEach { guardar(it, 1) }
        }
        ActivityScenario.launch(ColeccionActivity::class.java).use { escenario ->
            escenario.onActivity { actividad ->
                val lista = actividad.findViewById<ListView>(R.id.lista_coleccion)
                esperarHasta { palabras(lista).size == 6 }

                // El orden del español: la tilde no separa, la Ñ va después de la N.
                assertEquals(
                    listOf("1979", "ámbar", "árbol", "azul", "baldaquino", "ñandú"),
                    palabras(lista),
                )
                // Cada grupo abre con su letra; lo que no empieza por letra va a "#".
                assertEquals(listOf("#", "A", "B", "Ñ"), letras(lista))
            }
        }
    }

    @Test
    fun elBuscadorEncuentraSinImportarLasTildes() {
        runBlocking {
            dao.todas().forEach { dao.quitar(it.palabra) }
            listOf("árbol", "azul", "ñandú").forEach { guardar(it, 1) }
        }
        ActivityScenario.launch(ColeccionActivity::class.java).use { escenario ->
            escenario.onActivity { actividad ->
                val lista = actividad.findViewById<ListView>(R.id.lista_coleccion)
                val buscador = actividad.findViewById<EditText>(R.id.buscador)
                esperarHasta { palabras(lista).size == 3 }

                buscador.setText("arbol")
                esperarHasta { palabras(lista) == listOf("árbol") }

                buscador.setText("NANDU")
                esperarHasta { palabras(lista) == listOf("ñandú") }

                // También busca dentro de la definición.
                buscador.setText("definición de azul")
                esperarHasta { palabras(lista) == listOf("azul") }

                buscador.setText("")
                esperarHasta { palabras(lista).size == 3 }
            }
        }
    }

    private fun filas(lista: ListView) = (0 until lista.adapter.count).map { posicion ->
        lista.adapter.getItemViewType(posicion) to lista.adapter.getView(posicion, null, lista)
    }

    private fun palabras(lista: ListView) = filas(lista)
        .filter { (tipo, _) -> tipo == 1 }
        .map { (_, vista) -> vista.findViewById<TextView>(R.id.item_palabra).text.toString() }

    private fun letras(lista: ListView) = filas(lista)
        .filter { (tipo, _) -> tipo == 0 }
        .map { (_, vista) -> vista.findViewById<TextView>(R.id.letra).text.toString() }

    private fun tocarEstrellaDe(palabra: String, lista: ListView) {
        val posicion = (0 until lista.adapter.count).first { posicion ->
            lista.adapter.getItemViewType(posicion) == 1 &&
                lista.adapter.getView(posicion, null, lista)
                    .findViewById<TextView>(R.id.item_palabra).text.toString() == palabra
        }
        lista.adapter.getView(posicion, null, lista)
            .findViewById<ImageButton>(R.id.item_estrella).performClick()
    }

    private fun esperarHasta(condicion: () -> Boolean) {
        repeat(300) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condicion()) return
            Thread.sleep(10)
        }
        fail("La condición no se cumplió a tiempo")
    }

    private fun esperarUnPoco() = repeat(30) {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(10)
    }
}
