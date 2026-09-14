package app.widgetdiccionario.data

import androidx.room.Dao
import androidx.room.Query

@Dao
interface PalabraDao {

    /** Con ids contiguos desde 1 (lo garantiza tools/build_db.py) equivale al total de palabras. */
    @Query("SELECT MAX(id) FROM palabras")
    suspend fun maxId(): Int?

    @Query("SELECT * FROM palabras WHERE id = :id")
    suspend fun porId(id: Int): Palabra?
}
