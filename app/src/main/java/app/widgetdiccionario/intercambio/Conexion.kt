package app.widgetdiccionario.intercambio

import android.content.Context
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/** Espera en un puerto libre a que el otro teléfono se conecte. */
class Anfitrion(private val servidor: ServerSocket = ServerSocket(0, 1)) : Closeable {

    val puerto: Int get() = servidor.localPort

    /** Bloquea hasta que alguien de la red local se conecta. Rechaza cualquier conexión de fuera. */
    fun esperarConexion(esperaMs: Int = ESPERA_MS): Canal {
        servidor.soTimeout = esperaMs
        while (true) {
            val socket = servidor.accept()
            if (RedLocal.esDeRedLocal(socket.inetAddress)) return canalDe(socket)
            runCatching { socket.close() }
        }
    }

    override fun close() {
        runCatching { servidor.close() }
    }

    private companion object {
        const val ESPERA_MS = 60_000
    }
}

/** Se conecta al teléfono que está esperando. */
object Visitante {

    /**
     * Ata el socket a la red Wi-Fi: si el teléfono tiene los datos móviles como red por defecto, una
     * conexión sin atar saldría por ahí y nunca llegaría al otro teléfono.
     */
    fun conectar(context: Context, direccion: InetAddress, puerto: Int, esperaMs: Int = 10_000): Canal {
        val socket = Socket()
        RedLocal.redWifi(context)?.let { wifi -> runCatching { wifi.bindSocket(socket) } }
        return conectar(socket, direccion, puerto, esperaMs)
    }

    /** Sin contexto: se usa en pruebas. */
    fun conectar(direccion: InetAddress, puerto: Int, esperaMs: Int = 10_000): Canal =
        conectar(Socket(), direccion, puerto, esperaMs)

    private fun conectar(socket: Socket, direccion: InetAddress, puerto: Int, esperaMs: Int): Canal {
        if (!RedLocal.esDeRedLocal(direccion)) {
            throw IOException("Solo se permiten conexiones dentro de la red local")
        }
        socket.connect(InetSocketAddress(direccion, puerto), esperaMs)
        return canalDe(socket)
    }
}

/** Un socket ya conectado, visto como canal de mensajes. */
private fun canalDe(socket: Socket): Canal {
    socket.soTimeout = LECTURA_MS
    socket.tcpNoDelay = true
    val entrada = InputStreamReader(socket.getInputStream(), Charsets.UTF_8)
    val salida = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
    return object : Canal by CanalTexto(entrada, salida) {
        override fun close() {
            runCatching { socket.close() }
        }
    }
}

/** Tiempo máximo esperando un mensaje: si el otro deja la pantalla abierta sin tocar nada, se corta. */
private const val LECTURA_MS = 120_000
