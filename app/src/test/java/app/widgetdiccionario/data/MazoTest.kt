package app.widgetdiccionario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

class MazoTest {

    @Test
    fun cadaIdApareceExactamenteUnaVez() {
        val random = Random(42)
        for (total in listOf(1, 2, 3, 4, 5, 40, 41, 64, 65, 1000, 93_000)) {
            repeat(5) {
                val mazo = Mazo(total, random.nextLong())
                val cartas = (0 until total).map(mazo::carta)
                assertEquals("total=$total", (1..total).toSet(), cartas.toSet())
                assertEquals("total=$total", total, cartas.size)
            }
        }
    }

    @Test
    fun clavesDistintasDanOrdenesDistintos() {
        val a = (0 until 40).map(Mazo(40, 1L)::carta)
        val b = (0 until 40).map(Mazo(40, 2L)::carta)
        assertNotEquals(a, b)
    }

    @Test
    fun elOrdenNoEsCorrelativo() {
        // Sobre muchas claves, la carta en la posición i no debería tender a ser i+1.
        val total = 40
        val coincidencias = (1..2000L).sumOf { clave ->
            val mazo = Mazo(total, clave * 7919)
            (0 until total).count { mazo.carta(it) == it + 1 }
        }
        // Esperado ≈ 1 coincidencia por mazo (2000 en total).
        assert(coincidencias in 1500..2500) { "coincidencias=$coincidencias" }
    }
}
