package app.widgetdiccionario.intercambio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lectura de lo que entrega el otro teléfono por NFC. */
class NfcEmparejamientoTest {

    private fun respuesta(texto: String) = texto.toByteArray() + byteArrayOf(0x90.toByte(), 0x00)

    @Test
    fun seLeeLaDireccionYElPuerto() {
        val leido = NfcEmparejamiento.interpretar(respuesta("polimatia:192.168.0.23:41234"))
        assertEquals("192.168.0.23", leido?.first?.hostAddress)
        assertEquals(41234, leido?.second)
    }

    @Test
    fun seDescartaCualquierOtraTarjeta() {
        listOf(
            respuesta("otra cosa"),
            respuesta("polimatia:192.168.0.23"),
            respuesta("polimatia:192.168.0.23:80"),        // puerto reservado
            respuesta("polimatia:8.8.8.8:41234"),          // fuera de la red local
            "polimatia:192.168.0.23:41234".toByteArray(),  // sin el código de éxito
            byteArrayOf(0x6A.toByte(), 0x82.toByte()),     // la tarjeta no tiene nuestros datos
        ).forEach { assertNull(it.toString(), NfcEmparejamiento.interpretar(it)) }
        assertNull(NfcEmparejamiento.interpretar(null))
    }
}
