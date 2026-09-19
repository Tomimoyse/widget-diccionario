package app.widgetdiccionario

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.widgetdiccionario.data.Favorita
import app.widgetdiccionario.data.FavoritasDatabase
import app.widgetdiccionario.widget.EstiloWidget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Comprobaciones de las pantallas sin dispositivo: que abren y que sus acciones principales funcionan. */
@RunWith(AndroidJUnit4::class)
class PantallasTest {

    private val contexto = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun preparar() = Ajustes.setEstiloWidget(contexto, EstiloWidget.PREDETERMINADO)

    /** Las pantallas cargan sus datos en segundo plano: se deja avanzar el hilo principal mientras tanto. */
    private fun esperar(condicion: () -> Boolean) {
        repeat(300) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condicion()) return
            Thread.sleep(10)
        }
        fail("La condición no se cumplió a tiempo")
    }

    @Test
    fun todasLasPantallasAbren() {
        listOf(
            MainActivity::class.java,
            ColeccionActivity::class.java,
            EstiloWidgetActivity::class.java,
            PantallaBloqueoActivity::class.java,
            TutorialSamsungActivity::class.java,
            OtraMarcaActivity::class.java,
            IntercambioActivity::class.java,
        ).forEach { pantalla ->
            ActivityScenario.launch(pantalla).use { it.onActivity { shadowOf(Looper.getMainLooper()).idle() } }
        }
    }

    @Test
    fun laPreguntaDeMarcaLlevaALaPantallaCorrecta() {
        ActivityScenario.launch(PantallaBloqueoActivity::class.java).use { escenario ->
            escenario.onActivity { actividad ->
                actividad.findViewById<View>(R.id.fila_samsung).performClick()
                assertEquals(
                    TutorialSamsungActivity::class.java.name,
                    shadowOf(actividad).nextStartedActivity.component?.className,
                )
                actividad.findViewById<View>(R.id.fila_otra).performClick()
                assertEquals(
                    OtraMarcaActivity::class.java.name,
                    shadowOf(actividad).nextStartedActivity.component?.className,
                )
            }
        }
    }

    @Test
    fun elEstiloSoloSeGuardaAlConfirmar() {
        ActivityScenario.launch(EstiloWidgetActivity::class.java).use { escenario ->
            escenario.onActivity { actividad ->
                val confirmar = actividad.findViewById<View>(R.id.boton_confirmar)
                assertFalse("sin cambios, Confirmar está desactivado", confirmar.isEnabled)

                // Elegir el segundo color de fondo (negro) cambia la vista previa pero no los ajustes.
                actividad.findViewById<LinearLayout>(R.id.colores_fondo).getChildAt(1).performClick()
                assertTrue(confirmar.isEnabled)
                assertEquals(null, Ajustes.estiloWidget(contexto).colorFondo)

                confirmar.performClick()
                assertEquals(0xFF000000.toInt(), Ajustes.estiloWidget(contexto).colorFondo)
                assertTrue(actividad.isFinishing)
            }
        }
    }

    @Test
    fun elIntercambioPideElAliasYLuegoQueElijasPalabras() {
        Ajustes.setAlias(contexto, "")
        runBlocking {
            FavoritasDatabase.cerrarParaPruebas()
            val dao = FavoritasDatabase.get(contexto).favoritaDao()
            dao.todas().forEach { dao.quitar(it.palabra) }
            dao.guardar(Favorita("anemoia", "f.", "Nostalgia de un tiempo no vivido.", 1))
        }

        ActivityScenario.launch(IntercambioActivity::class.java).use { escenario ->
            escenario.onActivity { actividad ->
                esperar { actividad.findViewById<View>(R.id.paso_alias).visibility == View.VISIBLE }

                actividad.findViewById<EditText>(R.id.campo_alias).setText("Tomi")
                actividad.findViewById<View>(R.id.boton_alias).performClick()
                assertEquals("Tomi", Ajustes.alias(contexto))
                assertEquals(View.VISIBLE, actividad.findViewById<View>(R.id.paso_elegir).visibility)

                // Sin palabras elegidas no se puede seguir.
                val continuar = actividad.findViewById<View>(R.id.boton_elegir)
                assertFalse(continuar.isEnabled)

                val lista = actividad.findViewById<ListView>(R.id.lista_ofrecer)
                esperar { lista.adapter.count == 1 }
                lista.performItemClick(lista.adapter.getView(0, null, lista), 0, 0)
                assertTrue(continuar.isEnabled)
            }
        }
    }

    @Test
    fun otraMarcaMuestraLosRequisitos() {
        ActivityScenario.launch(OtraMarcaActivity::class.java).use { escenario ->
            escenario.onActivity { actividad ->
                val texto = actividad.findViewById<TextView>(R.id.requisitos).text.toString()
                assertTrue(texto, texto.contains("Batería sin restricciones"))
                assertTrue(texto, texto.contains("Mostrar la palabra en la notificación"))
            }
        }
    }
}
