package app.widgetdiccionario.intercambio

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/** Comprobaciones para que el intercambio nunca salga de la red local. */
object RedLocal {

    /**
     * Direcciones privadas (192.168.x, 10.x, 172.16-31.x), de enlace local y las IPv6 únicas locales.
     * Cualquier otra cosa viene de internet y se rechaza.
     */
    fun esDeRedLocal(direccion: InetAddress): Boolean {
        if (direccion.isLoopbackAddress || direccion.isSiteLocalAddress || direccion.isLinkLocalAddress) return true
        // IPv6 "unique local" (fc00::/7): isSiteLocalAddress no las reconoce.
        val bytes = direccion.address
        return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
    }

    /**
     * La red Wi-Fi, aunque no sea la red por defecto: si el teléfono está usando datos móviles, los
     * sockets saldrían por ahí y no llegarían al otro teléfono.
     */
    @Suppress("DEPRECATION") // allNetworks: la alternativa (callbacks) es asíncrona y aquí hace falta ya.
    fun redWifi(context: Context): Network? {
        val redes = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return redes.allNetworks.firstOrNull { red ->
            redes.getNetworkCapabilities(red)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    /**
     * IPv4 propia **en la Wi-Fi**, para mostrarla o armar el código de conexión manual. Null si no hay
     * Wi-Fi: la dirección de los datos móviles también es privada (10.x) y llevaría a intentar un
     * intercambio que nunca podría funcionar.
     */
    fun direccionPropia(context: Context): Inet4Address? {
        val redes = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return redWifi(context)
            ?.let { redes.getLinkProperties(it) }
            ?.linkAddresses
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { it.isSiteLocalAddress }
    }

    /** Sin contexto: recorre las interfaces de red. Se usa en pruebas. */
    fun direccionPropia(): Inet4Address? = NetworkInterface.getNetworkInterfaces()
        ?.toList()
        ?.asSequence()
        ?.filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
        ?.flatMap { it.inetAddresses.toList().asSequence() }
        ?.filterIsInstance<Inet4Address>()
        ?.firstOrNull { it.isSiteLocalAddress }

    /**
     * Código para conectar a mano: último número de la dirección y el puerto. Sirve cuando los dos
     * teléfonos están en la misma subred, que es el caso normal de una Wi-Fi hogareña.
     */
    fun codigoDeConexion(direccion: Inet4Address, puerto: Int): String =
        "%d-%d".format(direccion.address[3].toInt() and 0xFF, puerto)

    /** Reconstruye la dirección del otro teléfono a partir del código y de la subred propia. */
    fun desdeCodigo(codigo: String, propia: Inet4Address): Pair<InetAddress, Int>? {
        val partes = codigo.trim().split("-", ":", " ").filter { it.isNotBlank() }
        if (partes.size != 2) return null
        val ultimo = partes[0].toIntOrNull()?.takeIf { it in 1..254 } ?: return null
        val puerto = partes[1].toIntOrNull()?.takeIf { it in 1024..65535 } ?: return null
        val bytes = propia.address.copyOf().also { it[3] = ultimo.toByte() }
        return InetAddress.getByAddress(bytes) to puerto
    }
}
