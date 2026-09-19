package app.widgetdiccionario.intercambio

/** Palabra ofrecida en un intercambio. */
data class PalabraOfrecida(val palabra: String, val categoria: String?, val definicion: String)

/** Mensajes que se cruzan los dos teléfonos. */
sealed interface Mensaje {
    data class Hola(val version: Int, val alias: String) : Mensaje
    data class Codigo(val codigo: String) : Mensaje
    data class Oferta(val palabras: List<PalabraOfrecida>) : Mensaje
    data class Respuesta(val aceptada: Boolean) : Mensaje
    data object Fin : Mensaje
}

/**
 * Formato de los mensajes: una línea por mensaje, con los campos separados por tabuladores. Se evita
 * JSON a propósito, para que el protocolo no dependa de Android y se pueda probar en la PC.
 */
object Protocolo {

    const val VERSION = 1

    /** Tope de palabras por oferta: evita que un desconocido en la red mande algo enorme. */
    const val MAXIMO_PALABRAS = 500

    private const val SEP = '\t'

    fun codificar(mensaje: Mensaje): String = when (mensaje) {
        is Mensaje.Hola -> campos("HOLA", mensaje.version.toString(), mensaje.alias)
        is Mensaje.Codigo -> campos("CODIGO", mensaje.codigo)
        is Mensaje.Oferta -> campos(
            "OFERTA",
            *mensaje.palabras.flatMap { listOf(it.palabra, it.categoria.orEmpty(), it.definicion) }.toTypedArray(),
        )
        is Mensaje.Respuesta -> campos("RESPUESTA", if (mensaje.aceptada) "SI" else "NO")
        Mensaje.Fin -> campos("FIN")
    }

    /** Devuelve null si la línea no se entiende: ante cualquier cosa rara, se corta la conversación. */
    fun decodificar(linea: String): Mensaje? {
        val partes = linea.split(SEP).map(::desescapar)
        return when (partes.firstOrNull()) {
            "HOLA" -> partes.getOrNull(2)?.let { alias ->
                partes[1].toIntOrNull()?.let { version -> Mensaje.Hola(version, alias) }
            }

            "CODIGO" -> partes.getOrNull(1)?.let(Mensaje::Codigo)

            "OFERTA" -> {
                val datos = partes.drop(1)
                if (datos.size % 3 != 0 || datos.size / 3 > MAXIMO_PALABRAS) return null
                Mensaje.Oferta(
                    datos.chunked(3) { (palabra, categoria, definicion) ->
                        PalabraOfrecida(palabra, categoria.ifBlank { null }, definicion)
                    },
                )
            }

            "RESPUESTA" -> when (partes.getOrNull(1)) {
                "SI" -> Mensaje.Respuesta(aceptada = true)
                "NO" -> Mensaje.Respuesta(aceptada = false)
                else -> null
            }

            "FIN" -> Mensaje.Fin
            else -> null
        }
    }

    private fun campos(vararg valores: String) = valores.joinToString(SEP.toString(), transform = ::escapar)

    private fun escapar(valor: String) = valor
        .replace("\\", "\\\\")
        .replace("\t", "\\t")
        .replace("\n", "\\n")

    private fun desescapar(valor: String): String {
        val salida = StringBuilder(valor.length)
        var i = 0
        while (i < valor.length) {
            val caracter = valor[i]
            if (caracter == '\\' && i + 1 < valor.length) {
                i++
                salida.append(
                    when (valor[i]) {
                        't' -> '\t'
                        'n' -> '\n'
                        else -> valor[i]
                    },
                )
            } else {
                salida.append(caracter)
            }
            i++
        }
        return salida.toString()
    }
}
