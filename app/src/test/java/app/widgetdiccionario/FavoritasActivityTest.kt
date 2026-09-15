package app.widgetdiccionario

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ListView
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

/** Reproduce el recorrido del usuario: quitar una favorita, volver al menú y abrir la lista otra vez. */
@RunWith(AndroidJUnit4::class)
class FavoritasActivityTest {

    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private val dao get() = FavoritasDatabase.get(contexto).favoritaDao()

    @Before
    fun preparar() = runBlocking {
        dao.todas().forEach { dao.quitar(it.palabra) }
        dao.guardar(Favorita("anemoia", "f.", "Nostalgia de un tiempo no vivido.", agregadaEn = 2))
        dao.guardar(Favorita("baldaquino", "m.", "Dosel sobre columnas.", agregadaEn = 1))
    }

    @Test
    fun quitarUnaFavorita_noReapareceAlVolverAAbrirLaLista() {
        ActivityScenario.launch(FavoritasActivity::class.java).use { escenario ->
            escenario.onActivity { actividad ->
                val lista = actividad.findViewById<ListView>(R.id.lista_favoritas)
                esperarHasta { lista.adapter.count == 2 }

                // La más reciente ("anemoia") es la primera fila: se toca su estrella.
                val fila = lista.adapter.getView(0, null, lista)
                fila.findViewById<ImageButton>(R.id.item_estrella).performClick()
                esperarHasta { lista.adapter.count == 1 }
            }
            // El usuario vuelve al menú principal enseguida.
        }

        ActivityScenario.launch(FavoritasActivity::class.java).use { escenario ->
            escenario.onActivity { actividad ->
                val lista = actividad.findViewById<ListView>(R.id.lista_favoritas)
                esperarHasta { lista.adapter.count > 0 }
                esperarUnPoco()
                assertEquals("favoritas al reabrir", 1, lista.adapter.count)
            }
        }
        assertEquals(listOf("baldaquino"), runBlocking { dao.todas().map { it.palabra } })
    }

    @Test
    fun deshacer_devuelveLaFavoritaALaListaYALaBase() {
        ActivityScenario.launch(FavoritasActivity::class.java).use { escenario ->
            escenario.onActivity { actividad ->
                val lista = actividad.findViewById<ListView>(R.id.lista_favoritas)
                val barra = actividad.findViewById<View>(R.id.barra_deshacer)
                esperarHasta { lista.adapter.count == 2 }

                lista.adapter.getView(0, null, lista).findViewById<ImageButton>(R.id.item_estrella).performClick()
                esperarHasta { barra.visibility == View.VISIBLE }
                assertEquals(listOf("baldaquino"), runBlocking { dao.todas().map { it.palabra } })

                actividad.findViewById<View>(R.id.boton_deshacer).performClick()
                esperarHasta { lista.adapter.count == 2 }
                esperarUnPoco()
            }
        }
        assertEquals(listOf("anemoia", "baldaquino"), runBlocking { dao.todas().map { it.palabra } })
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
