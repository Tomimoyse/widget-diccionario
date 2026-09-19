package app.widgetdiccionario.intercambio

/**
 * Lo que dos teléfonos se pasan al acercarse: quién eres y qué palabra ofreces. Va por NFC, así que el
 * intercambio por toque no necesita Wi-Fi ni red alguna.
 *
 * Se reutiliza el formato de [Protocolo] (una línea por mensaje) para no inventar otro.
 */
data class PaqueteNfc(val alias: String, val palabra: PalabraOfrecida) {

    fun texto(): String = listOf(
        Protocolo.codificar(Mensaje.Hola(Protocolo.VERSION, alias)),
        Protocolo.codificar(Mensaje.Oferta(listOf(palabra))),
    ).joinToString("\n")

    fun bytes(): ByteArray = texto().toByteArray(Charsets.UTF_8)

    /** Lo parte en trozos que entren en un comando NFC. */
    fun trozos(tamano: Int = TAMANO_TROZO): List<ByteArray> = bytes().toList()
        .chunked(tamano) { it.toByteArray() }
        .ifEmpty { listOf(ByteArray(0)) }

    companion object {
        /** Los comandos NFC cortos llevan hasta 255 bytes; se deja margen para la cabecera. */
        const val TAMANO_TROZO = 200

        /** Devuelve null si no se entiende: se descarta cualquier tarjeta que no sea de la app. */
        fun desdeTexto(texto: String): PaqueteNfc? {
            val mensajes = texto.lineSequence().mapNotNull(Protocolo::decodificar).toList()
            val hola = mensajes.filterIsInstance<Mensaje.Hola>().firstOrNull() ?: return null
            if (hola.version != Protocolo.VERSION) return null
            val palabra = mensajes.filterIsInstance<Mensaje.Oferta>().firstOrNull()?.palabras?.firstOrNull()
                ?: return null
            return PaqueteNfc(hola.alias, palabra)
        }

        fun desdeBytes(bytes: ByteArray): PaqueteNfc? = desdeTexto(String(bytes, Charsets.UTF_8))

        fun unirTrozos(trozos: List<ByteArray>): ByteArray {
            val salida = ByteArray(trozos.sumOf { it.size })
            var i = 0
            trozos.forEach { trozo ->
                trozo.copyInto(salida, i)
                i += trozo.size
            }
            return salida
        }
    }
}
