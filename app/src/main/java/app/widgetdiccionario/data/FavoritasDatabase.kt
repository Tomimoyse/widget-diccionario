package app.widgetdiccionario.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base propia de las favoritas, separada de [DiccionarioDatabase]: aquella se recopia desde assets en
 * cada actualización del diccionario (migración destructiva) y se llevaría las favoritas con ella.
 * Cualquier cambio de esquema aquí necesita una migración de verdad.
 */
@Database(entities = [Favorita::class], version = 1, exportSchema = false)
abstract class FavoritasDatabase : RoomDatabase() {

    abstract fun favoritaDao(): FavoritaDao

    companion object {
        @Volatile
        private var instancia: FavoritasDatabase? = null

        fun get(context: Context): FavoritasDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    FavoritasDatabase::class.java,
                    "favoritas.db",
                )
                    .build()
                    .also { instancia = it }
            }
    }
}
