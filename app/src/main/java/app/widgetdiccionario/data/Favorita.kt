package app.widgetdiccionario.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Palabra de la colección. Guarda su propia copia del texto (y no el id del diccionario) para
 * sobrevivir a una actualización del diccionario, que reemplaza la base precargada y renumera los ids.
 *
 * La tabla conserva el nombre «favoritas» de versiones anteriores: renombrarla obligaría a migrar la
 * base sin ganar nada.
 */
@Entity(tableName = "favoritas")
data class Favorita(
    @PrimaryKey val palabra: String,
    val categoria: String?,
    val definicion: String,
    val agregadaEn: Long,
    /** Alias de quien la envió en un intercambio; null si la guardaste tú. */
    val origen: String? = null,
) {
    companion object {
        fun de(palabra: Palabra, agregadaEn: Long = System.currentTimeMillis()) =
            Favorita(palabra.palabra, palabra.categoria, palabra.definicion, agregadaEn)
    }
}
