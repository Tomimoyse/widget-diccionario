package app.widgetdiccionario.intercambio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocoloTest {

    private fun ida_y_vuelta(mensaje: Mensaje) {
        val linea = Protocolo.codificar(mensaje)
        assertEquals("el mensaje ocupa una sola línea", -1, linea.indexOf('\n'))
        assertEquals(mensaje, Protocolo.decodificar(linea))
    }

    @Test
    fun losMensajesSobrevivenAlViaje() {
        ida_y_vuelta(Mensaje.Hola(Protocolo.VERSION, "Tomi"))
        ida_y_vuelta(Mensaje.Codigo("004213"))
        ida_y_vuelta(Mensaje.Respuesta(aceptada = true))
        ida_y_vuelta(Mensaje.Respuesta(aceptada = false))
        ida_y_vuelta(Mensaje.Fin)
        ida_y_vuelta(
            Mensaje.Oferta(
                listOf(
                    PalabraOfrecida("ñandú", "m.", "Ave corredora de América del Sur."),
                    PalabraOfrecida("anemoia", null, "Nostalgia de un tiempo que no viviste."),
                ),
            ),
        )
    }

    @Test
    fun lasDefinicionesConTabuladoresOSaltosDeLineaNoRompenElFormato() {
        val rara = PalabraOfrecida("prueba", "f.", "Con\ttabulador,\nsalto y barra \\ invertida")
        ida_y_vuelta(Mensaje.Oferta(listOf(rara)))
    }

    @Test
    fun loQueNoSeEntiendeSeDescarta() {
        listOf(
            "",
            "HOLA",
            "HOLA\tno-es-un-numero\tTomi",
            "OFERTA\tpalabra\tf.",           // faltan campos
            "RESPUESTA\tquizás",
            "CUALQUIERA\tcosa",
            "<html>respuesta de un servidor web</html>",
        ).forEach { assertNull(it, Protocolo.decodificar(it)) }
    }

    @Test
    fun seRechazaUnaOfertaDemasiadoGrande() {
        val enorme = (1..Protocolo.MAXIMO_PALABRAS + 1).map { PalabraOfrecida("palabra$it", null, "def") }
        assertNull(Protocolo.decodificar(Protocolo.codificar(Mensaje.Oferta(enorme))))
    }
}
