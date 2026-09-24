package app.widgetdiccionario.intercambio

import java.io.BufferedReader
import java.io.Closeable
import java.io.Reader
import java.io.Writer

/** Por dónde viajan los mensajes. Se abstrae para poder probar el protocolo sin red de por medio. */
interface Canal : Closeable {
    fun enviar(mensaje: Mensaje)

    /** Null si la otra punta cerró o mandó algo que no se entiende. */
    fun recibir(): Mensaje?
}

/** Canal de texto: un mensaje por línea. */
class CanalTexto(entrada: Reader, private val salida: Writer) : Canal {

    private val lector: BufferedReader = entrada.buffered()

    override fun enviar(mensaje: Mensaje) {
        salida.write(Protocolo.codificar(mensaje))
        salida.write("\n")
        salida.flush()
    }

    override fun recibir(): Mensaje? = lector.readLine()?.let(Protocolo::decodificar)

    override fun close() {
        runCatching { lector.close() }
        runCatching { salida.close() }
    }
}
