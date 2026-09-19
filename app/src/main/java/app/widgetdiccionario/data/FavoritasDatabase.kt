package app.widgetdiccionario.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Base propia de la colección, separada de [DiccionarioDatabase]: aquella se recopia desde assets en
 * cada actualización del diccionario (migración destructiva) y se llevaría la colección con ella.
 * Cualquier cambio de esquema aquí necesita una migración de verdad: nunca borrar y recrear.
 */
@Database(entities = [Favorita::class], version = 2, exportSchema = false)
abstract class FavoritasDatabase : RoomDatabase() {

    abstract fun favoritaDao(): FavoritaDao

    companion object {
        /** v2: columna «origen» con el alias de quien envió la palabra en un intercambio. */
        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE favoritas ADD COLUMN origen TEXT")
            }
        }

        @Volatile
        private var instancia: FavoritasDatabase? = null

        fun get(context: Context): FavoritasDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    FavoritasDatabase::class.java,
                    "favoritas.db",
                )
                    .addMigrations(MIGRACION_1_2)
                    .build()
                    .also { instancia = it }
            }

        /** Los tests corren en el mismo proceso: sin esto, una prueba arrastraría la base de la anterior. */
        @VisibleForTesting
        fun cerrarParaPruebas() {
            synchronized(this) {
                instancia?.close()
                instancia = null
            }
        }
    }
}
