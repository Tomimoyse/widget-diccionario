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
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Intercambio acercando los teléfonos, sin Wi-Fi ni red de por medio: la palabra entera viaja en el
 * toque. Android Beam ya no existe, así que un teléfono hace de tarjeta (este servicio) y el otro de
 * lector; el lector entrega su palabra y se lleva la de la tarjeta en la misma operación.
 */
class ServicioNfcIntercambio : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val apdu = commandApdu ?: return ERROR
        return when {
            esSelect(apdu) -> {
                recibidos.clear()
                entregoTodo = false
                Log.i(TAG, "tarjeta: nos seleccionaron (palabra propia lista: ${propio != null})")
                OK
            }

            apdu.size >= 5 && apdu[1] == INS_ENVIAR -> recibirTrozo(apdu)
            // Para entregar hace falta haber elegido palabra; recibir se puede igual.
            propio == null -> ERROR.also { Log.w(TAG, "nos piden la palabra y no hay ninguna elegida") }
            apdu.size >= 4 && apdu[1] == INS_TOTAL -> byteArrayOf(trozosPropios().size.toByte()) + OK
            apdu.size >= 4 && apdu[1] == INS_PEDIR -> entregarTrozo(apdu[2].toInt())
            else -> ERROR.also { Log.w(TAG, "comando NFC desconocido: ${apdu.joinToString("") { "%02X".format(it) }.take(12)}") }
        }
    }

    override fun onDeactivated(reason: Int) = Unit

    private fun recibirTrozo(apdu: ByteArray): ByteArray {
        val indice = apdu[2].toInt()
        val total = apdu[3].toInt()
        val largo = apdu[4].toInt() and 0xFF
        if (total <= 0 || indice !in 0 until total || apdu.size < 5 + largo) return ERROR
        recibidos[indice] = apdu.copyOfRange(5, 5 + largo)
        Log.i(TAG, "tarjeta: trozo ${indice + 1} de $total recibido")
        if (recibidos.size == total) {
            val paquete = PaqueteNfc.desdeBytes(PaqueteNfc.unirTrozos((0 until total).map { recibidos[it]!! }))
            recibidos.clear()
            Log.i(TAG, "tarjeta: paquete completo (entendido: ${paquete != null}, hay quien escuche: ${alRecibir != null})")
            if (paquete != null) principal.post { alRecibir?.invoke(paquete) }
        }
        return OK
    }

    private fun entregarTrozo(indice: Int): ByteArray {
        val trozos = trozosPropios()
        val trozo = trozos.getOrNull(indice) ?: return ERROR
        if (indice == trozos.lastIndex) entregoTodo = true
        return trozo + OK
    }

    private fun esSelect(apdu: ByteArray) =
        apdu.size >= 6 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
            apdu.joinToString("") { "%02X".format(it) }.contains(AID)

    private fun trozosPropios() = propio?.trozos().orEmpty()

    companion object {
        const val TAG = "IntercambioNfc"
        const val AID = "F0506F6C696D61746961"
        const val INS_ENVIAR: Byte = 0x10
        const val INS_PEDIR: Byte = 0x20
        const val INS_TOTAL: Byte = 0x21

        val OK = byteArrayOf(0x90.toByte(), 0x00)
        val ERROR = byteArrayOf(0x6A.toByte(), 0x82.toByte())

        private val principal = Handler(Looper.getMainLooper())
        private val recibidos = mutableMapOf<Int, ByteArray>()

        /** Lo que este teléfono ofrece mientras la pantalla de intercambio está abierta. */
        @Volatile
        var propio: PaqueteNfc? = null

        /** Avisa a la pantalla cuando llega la palabra del otro teléfono. */
        @Volatile
        var alRecibir: ((PaqueteNfc) -> Unit)? = null

        /** True cuando el lector se llevó toda nuestra palabra. */
        @Volatile
        var entregoTodo = false
    }
}

object IntercambioNfc {

    fun disponible(context: Context) = adaptador(context)?.isEnabled == true

    /** Vía NfcManager: en Android 16 NfcAdapter.getDefaultAdapter(context) devolvió null en un teléfono. */
    private fun adaptador(context: Context): NfcAdapter? =
        context.getSystemService(NfcManager::class.java)?.defaultAdapter ?: NfcAdapter.getDefaultAdapter(context)

    /** Deja este teléfono listo para que lo lean (hace de tarjeta). */
    fun ofrecer(paquete: PaqueteNfc, alRecibir: (PaqueteNfc) -> Unit) {
        ServicioNfcIntercambio.propio = paquete
        ServicioNfcIntercambio.entregoTodo = false
        ServicioNfcIntercambio.alRecibir = alRecibir
    }

    fun dejarDeOfrecer() {
        ServicioNfcIntercambio.propio = null
        ServicioNfcIntercambio.alRecibir = null
    }

    fun elOtroSeLlevoLaPalabra() = ServicioNfcIntercambio.entregoTodo

    /**
     * Mientras esta pantalla está al frente, el sistema entrega los toques a nuestro servicio sin
     * preguntar: sin esto, Android muestra un diálogo para elegir entre las apps que usan NFC.
     */
    fun preferirNuestraTarjeta(actividad: Activity) {
        val emulacion = adaptador(actividad)?.let(CardEmulation::getInstance) ?: return
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

    /**
     * Pasa a modo lector: al acercar el otro teléfono se entrega [propio] y se recibe lo suyo.
     *
     * En modo lector el teléfono deja de poder ser leído, así que si los dos tocan el botón no pasaría
     * nada. Por eso el lector se apaga unos instantes cada tanto, con pausas de duración irregular para
     * que los dos no queden siempre en el mismo estado.
     */
    fun leer(actividad: Activity, propio: PaqueteNfc, alRecibir: (PaqueteNfc) -> Unit) {
        enCiclo = true
        activarLector(actividad, propio, alRecibir)
        programarPausa(actividad, propio, alRecibir)
    }

    private fun programarPausa(actividad: Activity, propio: PaqueteNfc, alRecibir: (PaqueteNfc) -> Unit) {
        principal.postDelayed(
            {
                if (!enCiclo) return@postDelayed
                adaptador(actividad)?.disableReaderMode(actividad)
                principal.postDelayed(
                    {
                        if (!enCiclo) return@postDelayed
                        activarLector(actividad, propio, alRecibir)
                        programarPausa(actividad, propio, alRecibir)
                    },
                    PAUSA_MS + azar(),
                )
            },
            LECTURA_MS + azar(),
        )
    }

    private fun azar() = (0..600L).random()

    private fun activarLector(actividad: Activity, propio: PaqueteNfc, alRecibir: (PaqueteNfc) -> Unit) {
        val adaptador = adaptador(actividad) ?: return
        adaptador.enableReaderMode(
            actividad,
            { etiqueta ->
                val iso = IsoDep.get(etiqueta) ?: return@enableReaderMode
                runCatching {
                    iso.timeout = ESPERA_MS
                    iso.connect()
                    Log.i(TAG, "lector: tarjeta en rango (APDU extendido: ${iso.isExtendedLengthApduSupported})")
                    val recibido = conversar(iso, propio)
                    if (recibido != null) alRecibir(recibido)
                }.onFailure { Log.w(TAG, "lector: se cortó el toque", it) }
                runCatching { iso.close() }
            },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    fun dejarDeLeer(actividad: Activity) {
        enCiclo = false
        principal.removeCallbacksAndMessages(null)
        adaptador(actividad)?.disableReaderMode(actividad)
    }

    /** El diálogo completo con la tarjeta: saludar, entregar lo propio y pedir lo suyo. */
    private fun conversar(iso: IsoDep, propio: PaqueteNfc): PaqueteNfc? {
        if (datos(iso.transceive(select())) == null) {
            Log.w(TAG, "lector: la otra punta no respondió al saludo")
            return null
        }

        val mios = propio.trozos()
        mios.forEachIndexed { indice, trozo ->
            if (datos(iso.transceive(comando(ServicioNfcIntercambio.INS_ENVIAR, indice, mios.size, trozo))) == null) {
                Log.w(TAG, "lector: no aceptaron el trozo ${indice + 1} de ${mios.size}")
                return null
            }
        }
        Log.i(TAG, "lector: entregamos nuestra palabra en ${mios.size} trozo(s)")

        val respuestaTotal = iso.transceive(comando(ServicioNfcIntercambio.INS_TOTAL))
        val total = datos(respuestaTotal)?.firstOrNull()?.toInt()
        if (total == null || total !in 1..MAXIMO_TROZOS) {
            Log.w(TAG, "lector: no nos dicen cuántos trozos tienen (respuesta: ${respuestaTotal?.joinToString("") { "%02X".format(it) }})")
            return null
        }
        val suyos = (0 until total).map { indice ->
            datos(iso.transceive(comando(ServicioNfcIntercambio.INS_PEDIR, indice))) ?: run {
                Log.w(TAG, "lector: nos faltó el trozo ${indice + 1} de $total")
                return null
            }
        }
        val paquete = PaqueteNfc.desdeBytes(PaqueteNfc.unirTrozos(suyos))
        Log.i(TAG, "lector: recibimos $total trozo(s) (entendido: ${paquete != null})")
        return paquete
    }

    /** Separa los datos del código de resultado; null si la tarjeta no respondió que todo fue bien. */
    internal fun datos(respuesta: ByteArray?): ByteArray? {
        if (respuesta == null || respuesta.size < 2) return null
        val fin = respuesta.size - 2
        if (respuesta[fin] != 0x90.toByte() || respuesta[fin + 1] != 0x00.toByte()) return null
        return respuesta.copyOfRange(0, fin)
    }

    internal fun comando(ins: Byte, p1: Int = 0, p2: Int = 0, datos: ByteArray = ByteArray(0)): ByteArray =
        byteArrayOf(0x00, ins, p1.toByte(), p2.toByte(), datos.size.toByte()) + datos

    private fun select(): ByteArray {
        val aid = ServicioNfcIntercambio.AID.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid + byteArrayOf(0x00)
    }

    private val principal = Handler(Looper.getMainLooper())

    @Volatile
    private var enCiclo = false

    private const val TAG = "IntercambioNfc"
    private const val ESPERA_MS = 3_000

    /** Cuánto escucha y cuánto se deja leer en cada vuelta. */
    private const val LECTURA_MS = 1_200L
    private const val PAUSA_MS = 700L
    private const val MAXIMO_TROZOS = 32
}
