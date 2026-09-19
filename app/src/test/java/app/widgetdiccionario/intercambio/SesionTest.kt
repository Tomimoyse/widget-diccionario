package app.widgetdiccionario.intercambio

import java.io.PipedReader
import java.io.PipedWriter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Un intercambio completo entre dos "teléfonos" conectados por tuberías, sin red de por medio. */
class SesionTest {

    private val palabrasDeAna = listOf(PalabraOfrecida("anemoia", "f.", "Nostalgia de un tiempo no vivido."))
    private val palabrasDeBeto = listOf(
        PalabraOfrecida("baldaquino", "m.", "Dosel sobre columnas."),
        PalabraOfrecida("ñandú", "m.", "Ave corredora."),
    )

    @Test
    fun losDosSeSaludan_intercambianYVenElMismoCodigo() {
        val (canalAna, canalBeto) = canalesConectados()
        val anfitriona = Sesion(canalAna, anfitrion = true)
        val visitante = Sesion(canalBeto, anfitrion = false)
        val hilos = Executors.newFixedThreadPool(2)

        val ana = hilos.submit<Resultado> {
            val saludo = anfitriona.saludar("Ana")
            anfitriona.ofrecer(palabrasDeAna)
            val recibidas = anfitriona.esperarOferta()
            anfitriona.responder(aceptada = true)
            Resultado(saludo, recibidas, anfitriona.esperarRespuesta())
        }
        val beto = hilos.submit<Resultado> {
            val saludo = visitante.saludar("Beto")
            visitante.ofrecer(palabrasDeBeto)
            val recibidas = visitante.esperarOferta()
            visitante.responder(aceptada = true)
            Resultado(saludo, recibidas, visitante.esperarRespuesta())
        }

        val deAna = ana.get(5, TimeUnit.SECONDS)
        val deBeto = beto.get(5, TimeUnit.SECONDS)
        hilos.shutdown()

        assertEquals("Beto", deAna.saludo.alias)
        assertEquals("Ana", deBeto.saludo.alias)
        assertEquals("los dos ven el mismo código", deAna.saludo.codigo, deBeto.saludo.codigo)
        assertTrue("el código es de 6 dígitos", deAna.saludo.codigo.matches(Regex("\\d{6}")))
        assertEquals(palabrasDeBeto, deAna.recibidas)
        assertEquals(palabrasDeAna, deBeto.recibidas)
        assertTrue(deAna.elOtroAcepto)
        assertTrue(deBeto.elOtroAcepto)
    }

    @Test
    fun siLaOtraPuntaCortaSeAvisaConUnError() {
        val (canalAna, canalBeto) = canalesConectados()
        canalBeto.close()
        val error = runCatching { Sesion(canalAna, anfitrion = true).saludar("Ana") }.exceptionOrNull()
        assertTrue("$error", error is Sesion.ErrorDeIntercambio)
    }

    private data class Resultado(
        val saludo: Sesion.Saludo,
        val recibidas: List<PalabraOfrecida>,
        val elOtroAcepto: Boolean,
    )

    private fun canalesConectados(): Pair<Canal, Canal> {
        val deAnaABeto = PipedWriter()
        val deBetoAAna = PipedWriter()
        val leeBeto = PipedReader(deAnaABeto)
        val leeAna = PipedReader(deBetoAAna)
        return CanalTexto(leeAna, deAnaABeto) to CanalTexto(leeBeto, deBetoAAna)
    }
}
