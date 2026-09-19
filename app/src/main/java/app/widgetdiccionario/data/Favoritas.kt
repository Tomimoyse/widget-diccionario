package app.widgetdiccionario.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Operaciones sobre la colección de palabras, compartidas por el widget y la app. */
object Favoritas {

    /** Evita que dos toques seguidos en la estrella lean el mismo estado y se pisen. */
    private val mutex = Mutex()

    suspend fun esFavorita(context: Context, palabra: Palabra): Boolean =
        dao(context).esFavorita(palabra.palabra)

    /** Marca o desmarca [palabra] y devuelve el estado nuevo (true = favorita). */
    suspend fun alternar(context: Context, palabra: Palabra): Boolean = mutex.withLock {
        val dao = dao(context)
        if (dao.esFavorita(palabra.palabra)) {
            dao.quitar(palabra.palabra)
            false
        } else {
            dao.guardar(Favorita.de(palabra))
            true
        }
    }

    suspend fun todas(context: Context): List<Favorita> = dao(context).todas()

    suspend fun contar(context: Context): Int = dao(context).contar()

    suspend fun quitar(context: Context, favorita: Favorita) = mutex.withLock {
        dao(context).quitar(favorita.palabra)
    }

    /** Vuelve a guardar una palabra recién quitada, conservando su fecha y su origen. */
    suspend fun restaurar(context: Context, favorita: Favorita) = mutex.withLock {
        dao(context).guardar(favorita)
    }

    /**
     * Guarda las palabras recibidas en un intercambio, anotando de quién vinieron. Las que ya están en
     * la colección no se tocan: conservan su fecha y su origen.
     */
    suspend fun recibir(context: Context, palabras: List<Favorita>, de: String): Int = mutex.withLock {
        val dao = dao(context)
        var guardadas = 0
        palabras.forEach { palabra ->
            if (!dao.esFavorita(palabra.palabra)) {
                dao.guardar(palabra.copy(agregadaEn = System.currentTimeMillis(), origen = de))
                guardadas++
            }
        }
        guardadas
    }

    private fun dao(context: Context) = FavoritasDatabase.get(context).favoritaDao()
}
