package app.widgetdiccionario.intercambio

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.cardemulation.CardEmulation
import android.nfc.cardemulation.HostApduService
import android.nfc.tech.IsoDep
import android.os.Bundle
import java.net.InetAddress

/**
 * Emparejamiento acercando los teléfonos. Android Beam ya no existe, así que un teléfono se comporta
 * como tarjeta NFC (este servicio) y el otro como lector: por ahí solo viaja la dirección local y el
 * puerto; las palabras siguen yendo por la Wi-Fi.
 */
class ServicioNfcIntercambio : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val datos = emparejamiento ?: return SIN_DATOS
        if (commandApdu == null || !esNuestroSelect(commandApdu)) return SIN_DATOS
        // Nos acaban de leer: la conexión que llegue enseguida viene de ese toque.
        entregadoEn = System.currentTimeMillis()
        return datos.toByteArray(Charsets.UTF_8) + TODO_BIEN
    }

    override fun onDeactivated(reason: Int) = Unit

    private fun esNuestroSelect(apdu: ByteArray): Boolean {
        if (apdu.size < 6 || apdu[0] != 0x00.toByte() || apdu[1] != 0xA4.toByte()) return false
        return apdu.toHex().contains(AID)
    }

    companion object {
        /** Lo que se entrega al acercar: dirección y puerto donde espera este teléfono. */
        @Volatile
        var emparejamiento: String? = null

        /** Cuándo nos leyeron por última vez. */
        @Volatile
        var entregadoEn: Long = 0

        const val AID = "F0506F6C696D61746961"
        private val TODO_BIEN = byteArrayOf(0x90.toByte(), 0x00)
        private val SIN_DATOS = byteArrayOf(0x6A.toByte(), 0x82.toByte())

        private fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }
    }
}

object NfcEmparejamiento {

    private const val PREFIJO = "polimatia:"

    fun disponible(context: Context) = adaptador(context)?.isEnabled == true

    /** Vía NfcManager: en Android 16 NfcAdapter.getDefaultAdapter(context) devolvió null en el teléfono. */
    private fun adaptador(context: Context): NfcAdapter? =
        context.getSystemService(NfcManager::class.java)?.defaultAdapter ?: NfcAdapter.getDefaultAdapter(context)

    /**
     * Mientras esta pantalla está al frente, el sistema entrega los toques a nuestro servicio sin
     * preguntar: sin esto, Android muestra un diálogo para elegir entre las apps que usan NFC.
     */
    fun preferirNuestraTarjeta(actividad: Activity) {
        val adaptador = adaptador(actividad) ?: return
        val emulacion = CardEmulation.getInstance(adaptador) ?: return
        runCatching {
            emulacion.setPreferredService(
                actividad,
                ComponentName(actividad, ServicioNfcIntercambio::class.java),
            )
        }
    }

    fun dejarDePreferir(actividad: Activity) {
        val adaptador = adaptador(actividad) ?: return
        runCatching { CardEmulation.getInstance(adaptador)?.unsetPreferredService(actividad) }
    }

    /** Lo que este teléfono ofrece por NFC mientras espera una conexión. */
    fun anunciar(direccion: InetAddress, puerto: Int) {
        ServicioNfcIntercambio.emparejamiento = "$PREFIJO${direccion.hostAddress}:$puerto"
    }

    fun dejarDeAnunciar() {
        ServicioNfcIntercambio.emparejamiento = null
    }

    /**
     * Si los teléfonos se acercaron hace un momento, el emparejamiento ya está hecho a mano y no hace
     * falta comparar ningún número. El que lee lo sabe al leer; el que hace de tarjeta, por esta marca.
     */
    fun huboToqueReciente(ahora: Long = System.currentTimeMillis()) =
        ServicioNfcIntercambio.entregadoEn != 0L &&
            ahora - ServicioNfcIntercambio.entregadoEn < VALIDEZ_DEL_TOQUE_MS

    fun olvidarToque() {
        ServicioNfcIntercambio.entregadoEn = 0
    }

    private const val VALIDEZ_DEL_TOQUE_MS = 120_000L

    /** Pone el teléfono en modo lector: al acercar el otro, [alLeer] recibe dirección y puerto. */
    fun leer(actividad: Activity, alLeer: (InetAddress, Int) -> Unit) {
        val adaptador = adaptador(actividad) ?: return
        adaptador.enableReaderMode(
            actividad,
            { etiqueta ->
                val iso = IsoDep.get(etiqueta) ?: return@enableReaderMode
                runCatching {
                    iso.connect()
                    val respuesta = iso.transceive(selectDeNuestroAid())
                    interpretar(respuesta)?.let { (direccion, puerto) -> alLeer(direccion, puerto) }
                }
                runCatching { iso.close() }
            },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    fun dejarDeLeer(actividad: Activity) {
        adaptador(actividad)?.disableReaderMode(actividad)
    }

    /** Respuesta esperada: el texto del emparejamiento seguido de 0x9000. */
    internal fun interpretar(respuesta: ByteArray?): Pair<InetAddress, Int>? {
        if (respuesta == null || respuesta.size < 3) return null
        val fin = respuesta.size - 2
        if (respuesta[fin] != 0x90.toByte() || respuesta[fin + 1] != 0x00.toByte()) return null
        val texto = String(respuesta, 0, fin, Charsets.UTF_8)
        if (!texto.startsWith(PREFIJO)) return null
        val partes = texto.removePrefix(PREFIJO).split(":")
        if (partes.size != 2) return null
        val puerto = partes[1].toIntOrNull()?.takeIf { it in 1024..65535 } ?: return null
        val direccion = runCatching { InetAddress.getByName(partes[0]) }.getOrNull() ?: return null
        return if (RedLocal.esDeRedLocal(direccion)) direccion to puerto else null
    }

    private fun selectDeNuestroAid(): ByteArray {
        val aid = ServicioNfcIntercambio.AID.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid + byteArrayOf(0x00)
    }
}
