package app.widgetdiccionario.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos de solo lectura precargada desde assets. Para publicar un diccionario nuevo:
 * regenera el .db con tools/build_db.py y sube [VERSION]; Room volverá a copiar el asset.
 */
@Database(entities = [Palabra::class], version = DiccionarioDatabase.VERSION, exportSchema = false)
abstract class DiccionarioDatabase : RoomDatabase() {

    abstract fun palabraDao(): PalabraDao

    companion object {
        const val VERSION = 2

        @Volatile
        private var instancia: DiccionarioDatabase? = null

        fun get(context: Context): DiccionarioDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    DiccionarioDatabase::class.java,
                    "diccionario.db",
                )
                    .createFromAsset("databases/diccionario.db")
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instancia = it }
            }
    }
}
