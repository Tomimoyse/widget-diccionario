package app.widgetdiccionario.data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * Orden alfabético español. SQLite ordena por bytes y mandaría «árbol» o «ñandú» al final, así que la
 * colección se ordena en la app con las reglas del idioma.
 */
object OrdenAlfabetico {

    private val ESPANOL = Locale.forLanguageTag("es")

    private val collator: Collator = Collator.getInstance(ESPANOL).apply {
        // Ignora mayúsculas: «Ávila» y «ávila» quedan juntas.
        strength = Collator.SECONDARY
    }

    val comparadorDePalabras: Comparator<Favorita> =
        Comparator { a, b -> collator.compare(a.palabra, b.palabra) }

    /**
     * Letra que encabeza el grupo: en mayúscula y sin tilde, pero la Ñ es una letra propia del español
     * y no se mezcla con la N. Lo que no empieza por letra va a un grupo aparte.
     */
    fun letraInicial(palabra: String): String {
        val primera = palabra.trim().firstOrNull()?.uppercase(ESPANOL)?.firstOrNull() ?: return OTROS
        if (primera == 'Ñ') return "Ñ"
        val sinTilde = Normalizer.normalize(primera.toString(), Normalizer.Form.NFD)
            .firstOrNull { it.isLetter() } ?: return OTROS
        return if (sinTilde.isLetter()) sinTilde.toString() else OTROS
    }

    const val OTROS = "#"

    /** Texto comparable: sin tildes ni mayúsculas, para que «arbol» encuentre «árbol». */
    fun normalizar(texto: String): String = Normalizer.normalize(texto.lowercase(ESPANOL), Normalizer.Form.NFD)
        .filterNot { it.isMark() }

    /** Una palabra coincide si lo buscado aparece en ella o en su definición. */
    fun coincide(favorita: Favorita, consulta: String): Boolean {
        val buscado = normalizar(consulta).trim()
        if (buscado.isEmpty()) return true
        return normalizar(favorita.palabra).contains(buscado) ||
            normalizar(favorita.definicion).contains(buscado)
    }

    private fun Char.isMark() = when (Character.getType(this).toByte()) {
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
        else -> false
    }
}
