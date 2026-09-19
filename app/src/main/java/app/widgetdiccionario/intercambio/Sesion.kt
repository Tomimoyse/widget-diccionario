package app.widgetdiccionario.intercambio

import java.io.IOException
import java.security.SecureRandom

/**
 * Un intercambio mutuo, paso a paso. Los dos teléfonos hacen lo mismo salvo el código: lo genera el
 * anfitrión (quien espera la conexión) y el visitante lo recibe, para que ambos muestren el mismo.
 *
 * Orden: saludo → código en las dos pantallas → cada uno ofrece sus palabras → cada uno ve lo que le
 * ofrecen y responde. Cada lado guarda lo recibido solo si su dueño aceptó.
 */
class Sesion(private val canal: Canal, private val anfitrion: Boolean) {

    /** Cualquier problema del intercambio, incluidos los de red, llega a la interfaz como este error. */
    class ErrorDeIntercambio(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa)

    /** Datos del otro teléfono, más el código que ambos tienen que ver igual. */
    data class Saludo(val alias: String, val codigo: String)

    fun saludar(alias: String): Saludo {
        enviar(Mensaje.Hola(Protocolo.VERSION, alias))
        val hola = esperar<Mensaje.Hola>()
        if (hola.version != Protocolo.VERSION) {
            throw ErrorDeIntercambio("La otra app tiene una versión distinta del intercambio")
        }
        val codigo = if (anfitrion) {
            generarCodigo().also { enviar(Mensaje.Codigo(it)) }
        } else {
            esperar<Mensaje.Codigo>().codigo
        }
        return Saludo(hola.alias, codigo)
    }

    fun ofrecer(palabras: List<PalabraOfrecida>) = enviar(Mensaje.Oferta(palabras))

    fun esperarOferta(): List<PalabraOfrecida> = esperar<Mensaje.Oferta>().palabras

    fun responder(aceptada: Boolean) = enviar(Mensaje.Respuesta(aceptada))

    fun esperarRespuesta(): Boolean = esperar<Mensaje.Respuesta>().aceptada

    fun despedirse() {
        runCatching { canal.enviar(Mensaje.Fin) }
        canal.close()
    }

    private fun enviar(mensaje: Mensaje) = try {
        canal.enviar(mensaje)
    } catch (e: IOException) {
        throw ErrorDeIntercambio("Se cortó la conexión", e)
    }

    private inline fun <reified T : Mensaje> esperar(): T {
        val mensaje = try {
            canal.recibir()
        } catch (e: IOException) {
            throw ErrorDeIntercambio("Se cortó la conexión", e)
        } ?: throw ErrorDeIntercambio("Se cortó la conexión")
        return mensaje as? T ?: throw ErrorDeIntercambio("Mensaje inesperado del otro teléfono")
    }

    private companion object {
        val AZAR = SecureRandom()

        fun generarCodigo() = "%06d".format(AZAR.nextInt(1_000_000))
    }
}
