package app.widgetdiccionario.intercambio

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** El diálogo por NFC: el lector entrega su palabra y se lleva la de la tarjeta en el mismo toque. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class IntercambioNfcTest {

    private val tarjeta = Robolectric.buildService(ServicioNfcIntercambio::class.java).create().get()

    private val deLaTarjeta = PaqueteNfc("Huenu", PalabraOfrecida("ñandú", "m.", "Ave corredora."))
    private val delLector = PaqueteNfc("Tomi", PalabraOfrecida("anemoia", "f.", "á".repeat(260)))

    @After
    fun limpiar() = IntercambioNfc.dejarDeOfrecer()

    @Test
    fun unToqueAlcanzaParaQueLosDosRecibanLaPalabraDelOtro() {
        var recibidoPorLaTarjeta: PaqueteNfc? = null
        IntercambioNfc.ofrecer(deLaTarjeta) { recibidoPorLaTarjeta = it }

        // 1. El lector saluda.
        assertArrayEquals(ServicioNfcIntercambio.OK, tarjeta.processCommandApdu(select(), null))

        // 2. Le entrega su palabra, en trozos.
        val mios = delLector.trozos()
        mios.forEachIndexed { indice, trozo ->
            val respuesta = tarjeta.processCommandApdu(
                IntercambioNfc.comando(ServicioNfcIntercambio.INS_ENVIAR, indice, mios.size, trozo),
                null,
            )
            assertArrayEquals(ServicioNfcIntercambio.OK, respuesta)
        }
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("la tarjeta recibió la palabra del lector", delLector, recibidoPorLaTarjeta)

        // 3. Y se lleva la de la tarjeta.
        val total = IntercambioNfc.datos(
            tarjeta.processCommandApdu(IntercambioNfc.comando(ServicioNfcIntercambio.INS_TOTAL), null),
        )!!.first().toInt()
        val suyos = (0 until total).map { indice ->
            IntercambioNfc.datos(
                tarjeta.processCommandApdu(IntercambioNfc.comando(ServicioNfcIntercambio.INS_PEDIR, indice), null),
            )!!
        }
        assertEquals(deLaTarjeta, PaqueteNfc.desdeBytes(PaqueteNfc.unirTrozos(suyos)))
        assertEquals(true, IntercambioNfc.elOtroSeLlevoLaPalabra())
    }

    @Test
    fun sinPalabraPreparadaNoSeEntregaNada() {
        IntercambioNfc.dejarDeOfrecer()
        tarjeta.processCommandApdu(select(), null)
        val respuesta = tarjeta.processCommandApdu(IntercambioNfc.comando(ServicioNfcIntercambio.INS_TOTAL), null)
        assertArrayEquals(ServicioNfcIntercambio.ERROR, respuesta)
    }

    @Test
    fun soloSeAceptaLoQueRespondeQueTodoFueBien() {
        assertNull(IntercambioNfc.datos(null))
        assertNull(IntercambioNfc.datos(ServicioNfcIntercambio.ERROR))
        assertArrayEquals(byteArrayOf(7), IntercambioNfc.datos(byteArrayOf(7) + ServicioNfcIntercambio.OK))
    }

    private fun select(): ByteArray {
        val aid = ServicioNfcIntercambio.AID.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid + byteArrayOf(0x00)
    }
}
