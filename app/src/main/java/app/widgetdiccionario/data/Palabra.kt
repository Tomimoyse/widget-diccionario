package app.widgetdiccionario.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Una entrada del diccionario. El esquema debe coincidir exactamente con el de
 * `assets/databases/diccionario.db` (lo genera tools/build_db.py).
 * Los ids son contiguos desde 1 para poder elegir una palabra al azar sin recorrer la tabla.
 */
@Entity(tableName = "palabras")
data class Palabra(
    @PrimaryKey val id: Int,
    val palabra: String,
    val categoria: String?,
    val definicion: String,
)
