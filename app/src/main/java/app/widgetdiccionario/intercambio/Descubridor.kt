package app.widgetdiccionario.intercambio

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.net.InetAddress

/** Otro teléfono visto en la red local. */
data class Vecino(val alias: String, val direccion: InetAddress, val puerto: Int)

/**
 * Anuncia y encuentra otros teléfonos en la misma Wi-Fi con NSD (mDNS), lo mismo que usan las
 * impresoras de red. Algunas redes (de invitados, públicas o con «aislamiento de clientes») bloquean
 * este descubrimiento: para esos casos está la conexión con código.
 */
class Descubridor(context: Context) {

    private val nsd = context.getSystemService(NsdManager::class.java)
    private var registro: NsdManager.RegistrationListener? = null
    private var busqueda: NsdManager.DiscoveryListener? = null

    /** Se anuncia en la red como «alias» en el puerto donde espera el anfitrión. */
    fun anunciarse(alias: String, puerto: Int) {
        detenerAnuncio()
        val info = NsdServiceInfo().apply {
            serviceName = alias.take(30).ifBlank { "Polimatía" }
            serviceType = TIPO
            port = puerto
        }
        val escucha = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, codigo: Int) {
                Log.w(TAG, "No se pudo anunciar el intercambio (código $codigo)")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, codigo: Int) = Unit
        }
        registro = escucha
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, escucha) }
            .onFailure { Log.w(TAG, "No se pudo anunciar el intercambio", it) }
    }

    /** Busca a los demás. [alEncontrar] llega en un hilo cualquiera. */
    fun buscar(alEncontrar: (Vecino) -> Unit) {
        detenerBusqueda()
        val escucha = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(tipo: String) = Unit
            override fun onServiceFound(info: NsdServiceInfo) = resolver(info, alEncontrar)
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(tipo: String) = Unit
            override fun onStartDiscoveryFailed(tipo: String, codigo: Int) {
                Log.w(TAG, "No se pudo buscar en la red (código $codigo)")
            }

            override fun onStopDiscoveryFailed(tipo: String, codigo: Int) = Unit
        }
        busqueda = escucha
        runCatching { nsd.discoverServices(TIPO, NsdManager.PROTOCOL_DNS_SD, escucha) }
            .onFailure { Log.w(TAG, "No se pudo buscar en la red", it) }
    }

    @Suppress("DEPRECATION") // registerServiceInfoCallback existe desde API 34; minSdk es 26.
    private fun resolver(info: NsdServiceInfo, alEncontrar: (Vecino) -> Unit) {
        nsd.resolveService(
            info,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, codigo: Int) = Unit

                override fun onServiceResolved(resuelto: NsdServiceInfo) {
                    val direccion = resuelto.host ?: return
                    if (!RedLocal.esDeRedLocal(direccion)) return
                    alEncontrar(Vecino(resuelto.serviceName ?: "", direccion, resuelto.port))
                }
            },
        )
    }

    fun detener() {
        detenerAnuncio()
        detenerBusqueda()
    }

    private fun detenerAnuncio() {
        registro?.let { runCatching { nsd.unregisterService(it) } }
        registro = null
    }

    private fun detenerBusqueda() {
        busqueda?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        busqueda = null
    }

    private companion object {
        const val TIPO = "_polimatia._tcp."
        const val TAG = "Descubridor"
    }
}
