package app.widgetdiccionario.intercambio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaqueteNfcTest {

    private val paquete = PaqueteNfc(
        alias = "Tomi",
        palabra = PalabraOfrecida("ñandú", "m.", "Ave corredora de América del Sur, parecida al avestruz."),
    )

    @Test
    fun elPaqueteSobreviveAlViaje() {
        assertEquals(paquete, PaqueteNfc.desdeBytes(paquete.bytes()))
    }

    @Test
    fun seParteYSeVuelveAArmar() {
        val larga = PaqueteNfc("Huenu", PalabraOfrecida("prueba", "f.", "á".repeat(280)))
        val trozos = larga.trozos()
        assertTrue("hacen falta varios trozos", trozos.size > 2)
        assertTrue("cada trozo entra en un comando", trozos.all { it.size <= PaqueteNfc.TAMANO_TROZO })
        assertEquals(larga, PaqueteNfc.desdeBytes(PaqueteNfc.unirTrozos(trozos)))
    }

    @Test
    fun seDescartaLoQueNoEsDeLaApp() {
        listOf("", "cualquier cosa", "HOLA\t1\tTomi", "OFERTA\tpalabra\tf.\tdefinición")
            .forEach { assertNull(it, PaqueteNfc.desdeTexto(it)) }
        // Otra versión del protocolo tampoco vale.
        assertNull(PaqueteNfc.desdeTexto("HOLA\t99\tTomi\nOFERTA\tp\tf.\td"))
    }
}
