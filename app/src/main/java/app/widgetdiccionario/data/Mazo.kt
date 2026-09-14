package app.widgetdiccionario.data

/**
 * Baraja virtual de los ids 1..[total]: recorriendo las posiciones 0..total-1 devuelve cada id
 * exactamente una vez, en orden aleatorio. En lugar de guardar la lista barajada usa una permutación
 * pseudoaleatoria (red de Feistel con [clave] + cycle walking), así que basta con persistir la clave
 * y la posición, sin importar el tamaño del diccionario.
 */
class Mazo(private val total: Int, private val clave: Long) {

    private val bitsMitad: Int
    private val mascara: Long

    init {
        require(total > 0) { "El mazo necesita al menos una carta" }
        var bits = 2
        while ((1L shl bits) < total) bits++
        if (bits % 2 == 1) bits++
        bitsMitad = bits / 2
        mascara = (1L shl bitsMitad) - 1
    }

    /** Id (1..total) de la carta en [posicion] (0..total-1). */
    fun carta(posicion: Int): Int {
        require(posicion in 0 until total)
        var x = posicion.toLong()
        // La permutación es sobre [0, 2^bits); se reaplica hasta caer dentro de [0, total).
        do x = permutar(x) while (x >= total)
        return (x + 1).toInt()
    }

    private fun permutar(x: Long): Long {
        var izq = x ushr bitsMitad
        var der = x and mascara
        repeat(RONDAS) { ronda ->
            val nuevaDer = izq xor (mezclar(der xor clave xor (ronda * GOLDEN)) and mascara)
            izq = der
            der = nuevaDer
        }
        return (izq shl bitsMitad) or der
    }

    private companion object {
        const val RONDAS = 4
        const val GOLDEN = -0x61c8864680b583ebL // 0x9e3779b97f4a7c15

        /** Finalizador de SplitMix64. */
        fun mezclar(valor: Long): Long {
            var z = valor
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xbf58476d1ce4e5b9
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94d049bb133111eb
            return z xor (z ushr 31)
        }
    }
}
