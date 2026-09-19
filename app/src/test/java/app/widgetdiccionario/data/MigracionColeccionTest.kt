package app.widgetdiccionario.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La base de la colección no se puede borrar y recrear: al subir de versión hay que conservar las
 * palabras guardadas. Aquí se arma una base con el esquema viejo (v1) y se comprueba que sobreviven.
 */
@RunWith(AndroidJUnit4::class)
class MigracionColeccionTest {

    private val contexto = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun aislarLaBase() = FavoritasDatabase.cerrarParaPruebas()

    @After
    fun soltarLaBase() = FavoritasDatabase.cerrarParaPruebas()

    @Test
    fun alSubirDeVersion_lasPalabrasSiguenAhiYElOrigenQuedaVacio() {
        val archivo = contexto.getDatabasePath("favoritas.db")
        archivo.parentFile?.mkdirs()
        archivo.delete()

        // Esquema de la versión 1, tal como lo creaba Room antes de la columna "origen".
        SQLiteDatabase.openOrCreateDatabase(archivo, null).use { vieja ->
            vieja.execSQL(
                "CREATE TABLE IF NOT EXISTS `favoritas` (`palabra` TEXT NOT NULL, `categoria` TEXT, " +
                    "`definicion` TEXT NOT NULL, `agregadaEn` INTEGER NOT NULL, PRIMARY KEY(`palabra`))",
            )
            vieja.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            vieja.execSQL("INSERT INTO favoritas VALUES ('anemoia', 'f.', 'Nostalgia de un tiempo no vivido.', 7)")
            vieja.version = 1
        }

        val guardadas = runBlocking { FavoritasDatabase.get(contexto).favoritaDao().todas() }

        assertEquals(1, guardadas.size)
        assertEquals("anemoia", guardadas[0].palabra)
        assertEquals("Nostalgia de un tiempo no vivido.", guardadas[0].definicion)
        assertEquals(7L, guardadas[0].agregadaEn)
        assertNull("lo que ya estaba no tiene origen", guardadas[0].origen)
    }
}
