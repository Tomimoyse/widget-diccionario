package app.widgetdiccionario.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoritaDao {

    @Query("SELECT EXISTS(SELECT 1 FROM favoritas WHERE palabra = :palabra)")
    suspend fun esFavorita(palabra: String): Boolean

    /** Las más recientes primero. */
    @Query("SELECT * FROM favoritas ORDER BY agregadaEn DESC")
    suspend fun todas(): List<Favorita>

    @Query("SELECT COUNT(*) FROM favoritas")
    suspend fun contar(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(favorita: Favorita)

    @Query("DELETE FROM favoritas WHERE palabra = :palabra")
    suspend fun quitar(palabra: String)
}
