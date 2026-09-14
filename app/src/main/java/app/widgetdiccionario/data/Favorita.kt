package app.widgetdiccionario.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Palabra marcada como favorita. Guarda su propia copia del texto (y no el id del diccionario) para
 * sobrevivir a una actualización del diccionario, que reemplaza la base precargada y renumera los ids.
 */
@Entity(tableName = "favoritas")
data class Favorita(
    @PrimaryKey val palabra: String,
    val categoria: String?,
    val definicion: String,
    val agregadaEn: Long,
) {
    companion object {
        fun de(palabra: Palabra, agregadaEn: Long = System.currentTimeMillis()) =
            Favorita(palabra.palabra, palabra.categoria, palabra.definicion, agregadaEn)
    }
}
