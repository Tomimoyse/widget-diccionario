package app.widgetdiccionario

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import app.widgetdiccionario.data.Favorita
import app.widgetdiccionario.data.Favoritas
import app.widgetdiccionario.data.OrdenAlfabetico
import app.widgetdiccionario.intercambio.Anfitrion
import app.widgetdiccionario.intercambio.Canal
import app.widgetdiccionario.intercambio.Descubridor
import app.widgetdiccionario.intercambio.NfcEmparejamiento
import app.widgetdiccionario.intercambio.PalabraOfrecida
import app.widgetdiccionario.intercambio.RedLocal
import app.widgetdiccionario.intercambio.Sesion
import app.widgetdiccionario.intercambio.Vecino
import app.widgetdiccionario.intercambio.Visitante
import java.io.IOException
import java.net.Inet4Address
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Intercambio de palabras entre dos teléfonos de la misma red Wi-Fi, paso a paso: alias, elegir qué
 * ofrecer, emparejarse, confirmar el mismo código en las dos pantallas y aceptar lo que llega.
 *
 * Todo vive mientras esta pantalla está abierta: al salir se cierran el anuncio en la red y el puerto.
 */
class IntercambioActivity : Activity() {

    private enum class Paso { ALIAS, ELEGIR, EMPAREJAR, CONFIRMAR, OFERTA, FINAL }

    private val scope = MainScope()
    private val adaptadorColeccion = AdaptadorColeccion()
    private val adaptadorVecinos = AdaptadorVecinos()
    private val adaptadorOferta = AdaptadorOferta()

    private var descubridor: Descubridor? = null
    private var anfitrion: Anfitrion? = null
    private var trabajo: Job? = null
    private var decision: CompletableDeferred<Boolean>? = null
    private var direccionPropia: Inet4Address? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intercambio)
        findViewById<ListView>(R.id.lista_ofrecer).apply {
            adapter = adaptadorColeccion
            setOnItemClickListener { _, _, posicion, _ -> adaptadorColeccion.alternar(posicion) }
        }
        findViewById<ListView>(R.id.lista_vecinos).apply {
            adapter = adaptadorVecinos
            setOnItemClickListener { _, _, posicion, _ -> conectarCon(adaptadorVecinos.getItem(posicion)) }
        }
        findViewById<ListView>(R.id.lista_oferta).adapter = adaptadorOferta

        findViewById<Button>(R.id.boton_alias).setOnClickListener { guardarAlias() }
        findViewById<Button>(R.id.boton_elegir).setOnClickListener { empezarAEmparejar() }
        findViewById<Button>(R.id.boton_usar_codigo).setOnClickListener {
            findViewById<View>(R.id.caja_codigo).visibility = View.VISIBLE
        }
        findViewById<Button>(R.id.boton_conectar).setOnClickListener { conectarConCodigo() }
        findViewById<Button>(R.id.boton_nfc).setOnClickListener { leerPorNfc() }
        findViewById<Button>(R.id.boton_confirmar_codigo).setOnClickListener { decidir(true) }
        findViewById<Button>(R.id.boton_cancelar_codigo).setOnClickListener { decidir(false) }
        findViewById<Button>(R.id.boton_aceptar).setOnClickListener { decidir(true) }
        findViewById<Button>(R.id.boton_rechazar).setOnClickListener { decidir(false) }
        findViewById<Button>(R.id.boton_final).setOnClickListener { finish() }

        scope.launch {
            adaptadorColeccion.cargar(Favoritas.todas(this@IntercambioActivity))
            if (Ajustes.alias(this@IntercambioActivity).isBlank()) irA(Paso.ALIAS) else irA(Paso.ELEGIR)
        }
    }

    override fun onDestroy() {
        soltarTodo()
        scope.cancel()
        super.onDestroy()
    }

    // --- Pasos ---

    private fun irA(paso: Paso) {
        val pasos = mapOf(
            Paso.ALIAS to R.id.paso_alias,
            Paso.ELEGIR to R.id.paso_elegir,
            Paso.EMPAREJAR to R.id.paso_emparejar,
            Paso.CONFIRMAR to R.id.paso_confirmar,
            Paso.OFERTA to R.id.paso_oferta,
            Paso.FINAL to R.id.paso_final,
        )
        pasos.forEach { (cual, id) ->
            findViewById<View>(id).visibility = if (cual == paso) View.VISIBLE else View.GONE
        }
        when (paso) {
            Paso.ALIAS -> encabezado(R.string.intercambio_alias_titulo, getString(R.string.intercambio_alias_desc))
            Paso.ELEGIR -> {
                encabezado(R.string.intercambio_elegir_titulo, getString(R.string.intercambio_elegir_desc))
                if (adaptadorColeccion.isEmpty) {
                    encabezado(R.string.intercambio_elegir_titulo, getString(R.string.intercambio_sin_coleccion))
                }
                actualizarContador()
            }

            Paso.EMPAREJAR -> encabezado(
                R.string.intercambio_emparejar_titulo,
                getString(R.string.intercambio_emparejar_desc),
            )

            else -> Unit
        }
    }

    private fun encabezado(titulo: Int, detalle: String) {
        findViewById<TextView>(R.id.titulo_paso).setText(titulo)
        findViewById<TextView>(R.id.detalle_paso).text = detalle
    }

    private fun guardarAlias() {
        val alias = findViewById<EditText>(R.id.campo_alias).text.toString().trim()
        if (alias.isBlank()) return
        Ajustes.setAlias(this, alias)
        irA(Paso.ELEGIR)
    }

    private fun actualizarContador() {
        findViewById<TextView>(R.id.contador_elegidas).text =
            resources.getQuantityString(
                R.plurals.intercambio_elegidas,
                adaptadorColeccion.elegidas().size,
                adaptadorColeccion.elegidas().size,
            )
        findViewById<Button>(R.id.boton_elegir).isEnabled = adaptadorColeccion.elegidas().isNotEmpty()
    }

    // --- Emparejar ---

    private fun empezarAEmparejar() {
        irA(Paso.EMPAREJAR)
        pedirPermisoDeRedLocal()
        avisarSiNoHayWifi()
        direccionPropia = RedLocal.direccionPropia()

        val servidor = runCatching { Anfitrion() }.getOrNull()
        if (servidor == null) {
            mostrarError(getString(R.string.intercambio_sin_wifi))
            return
        }
        anfitrion = servidor
        val propia = direccionPropia
        findViewById<TextView>(R.id.tu_codigo).text = if (propia == null) {
            getString(R.string.intercambio_sin_wifi)
        } else {
            getString(R.string.intercambio_tu_codigo, RedLocal.codigoDeConexion(propia, servidor.puerto))
        }

        prepararNfc(propia, servidor.puerto)

        val alias = Ajustes.alias(this)
        Descubridor(this).also { descubridor = it }.apply {
            anunciarse(alias, servidor.puerto)
            buscar { vecino -> scope.launch { if (!esUnoMismo(vecino)) adaptadorVecinos.agregar(vecino) } }
        }

        // Queda esperando a que el otro teléfono se conecte.
        trabajo = scope.launch {
            val canal = withContext(Dispatchers.IO) { runCatching { servidor.esperarConexion() }.getOrNull() }
            if (canal != null) intercambiar(canal, anfitrion = true)
        }
    }

    /** Con NFC este teléfono queda como "tarjeta"; el botón lo pasa a lector para leer al otro. */
    private fun prepararNfc(propia: Inet4Address?, puerto: Int) {
        if (propia == null || !NfcEmparejamiento.disponible(this)) return
        NfcEmparejamiento.anunciar(propia, puerto)
        findViewById<View>(R.id.aviso_nfc).visibility = View.VISIBLE
        findViewById<View>(R.id.boton_nfc).visibility = View.VISIBLE
    }

    private fun leerPorNfc() {
        findViewById<TextView>(R.id.estado_busqueda).apply {
            visibility = View.VISIBLE
            setText(R.string.intercambio_nfc_esperando)
        }
        NfcEmparejamiento.leer(this) { direccion, puerto ->
            scope.launch {
                NfcEmparejamiento.dejarDeLeer(this@IntercambioActivity)
                val canal = withContext(Dispatchers.IO) {
                    runCatching { Visitante.conectar(direccion, puerto) }.getOrNull()
                }
                if (canal == null) {
                    mostrarError(getString(R.string.intercambio_error, ""))
                } else {
                    intercambiar(canal, anfitrion = false)
                }
            }
        }
    }

    private fun esUnoMismo(vecino: Vecino) =
        vecino.puerto == anfitrion?.puerto && vecino.direccion.hostAddress == direccionPropia?.hostAddress

    private fun conectarCon(vecino: Vecino) {
        trabajo?.cancel()
        trabajo = scope.launch {
            val canal = withContext(Dispatchers.IO) {
                runCatching { Visitante.conectar(vecino.direccion, vecino.puerto) }.getOrNull()
            }
            if (canal == null) mostrarError(getString(R.string.intercambio_error, "")) else intercambiar(canal, false)
        }
    }

    private fun conectarConCodigo() {
        val propia = direccionPropia ?: return
        val destino = RedLocal.desdeCodigo(findViewById<EditText>(R.id.campo_codigo).text.toString(), propia)
        if (destino == null) {
            mostrarError(getString(R.string.intercambio_codigo_pista))
            return
        }
        trabajo?.cancel()
        trabajo = scope.launch {
            val canal = withContext(Dispatchers.IO) {
                runCatching { Visitante.conectar(destino.first, destino.second) }.getOrNull()
            }
            if (canal == null) mostrarError(getString(R.string.intercambio_error, "")) else intercambiar(canal, false)
        }
    }

    // --- El intercambio ---

    private suspend fun intercambiar(canal: Canal, anfitrion: Boolean) {
        soltarRed()
        val sesion = Sesion(canal, anfitrion)
        val alias = Ajustes.alias(this)
        val ofrecidas = adaptadorColeccion.elegidas().map { PalabraOfrecida(it.palabra, it.categoria, it.definicion) }
        try {
            val saludo = withContext(Dispatchers.IO) { sesion.saludar(alias) }
            mostrarCodigo(saludo)
            if (!esperarDecision()) {
                withContext(Dispatchers.IO) { sesion.despedirse() }
                finish()
                return
            }
            val oferta = withContext(Dispatchers.IO) {
                sesion.ofrecer(ofrecidas)
                sesion.esperarOferta()
            }
            mostrarOferta(saludo.alias, oferta)
            val acepto = esperarDecision()
            val guardadas = withContext(Dispatchers.IO) {
                sesion.responder(acepto)
                val guardadas = if (acepto) {
                    Favoritas.recibir(
                        this@IntercambioActivity,
                        oferta.map { Favorita(it.palabra, it.categoria, it.definicion, 0) },
                        de = saludo.alias,
                    )
                } else {
                    0
                }
                guardadas
            }
            val elOtroAcepto = withContext(Dispatchers.IO) { sesion.esperarRespuesta().also { sesion.despedirse() } }
            mostrarFinal(saludo.alias, guardadas, elOtroAcepto)
        } catch (e: Sesion.ErrorDeIntercambio) {
            withContext(Dispatchers.IO) { runCatching { sesion.despedirse() } }
            mostrarError(e.message.orEmpty())
        } catch (e: IOException) {
            withContext(Dispatchers.IO) { runCatching { sesion.despedirse() } }
            mostrarError(e.message.orEmpty())
        }
    }

    private fun mostrarCodigo(saludo: Sesion.Saludo) {
        encabezado(
            R.string.intercambio_confirmar_titulo,
            getString(R.string.intercambio_confirmar_desc, saludo.alias),
        )
        findViewById<TextView>(R.id.codigo_confirmacion).text = saludo.codigo
        irA(Paso.CONFIRMAR)
    }

    private fun mostrarOferta(alias: String, palabras: List<PalabraOfrecida>) {
        encabezado(R.string.intercambio_oferta_titulo, "")
        findViewById<TextView>(R.id.titulo_paso).text = getString(R.string.intercambio_oferta_titulo, alias)
        adaptadorOferta.cargar(palabras)
        irA(Paso.OFERTA)
    }

    private fun mostrarFinal(alias: String, guardadas: Int, elOtroAcepto: Boolean) {
        encabezado(R.string.intercambio_final_titulo, "")
        val resumen = buildString {
            append(
                if (guardadas > 0) {
                    resources.getQuantityString(R.plurals.intercambio_guardadas, guardadas, guardadas, alias)
                } else {
                    getString(R.string.intercambio_guardadas_ninguna)
                },
            )
            append("\n")
            append(
                if (elOtroAcepto) {
                    getString(R.string.intercambio_el_otro_acepto, alias)
                } else {
                    getString(R.string.intercambio_el_otro_rechazo, alias)
                },
            )
        }
        findViewById<TextView>(R.id.resumen_final).text = resumen
        irA(Paso.FINAL)
    }

    private fun mostrarError(detalle: String) {
        soltarTodo()
        encabezado(R.string.intercambio_final_titulo, "")
        findViewById<TextView>(R.id.resumen_final).text = getString(R.string.intercambio_error, detalle)
        findViewById<Button>(R.id.boton_final).setText(R.string.intercambio_cerrar)
        irA(Paso.FINAL)
    }

    /** Espera a que la persona toque uno de los dos botones del paso actual. */
    private suspend fun esperarDecision(): Boolean {
        val pendiente = CompletableDeferred<Boolean>()
        decision = pendiente
        return pendiente.await()
    }

    private fun decidir(respuesta: Boolean) {
        decision?.complete(respuesta)
        decision = null
    }

    // --- Red ---

    private fun pedirPermisoDeRedLocal() {
        // En Android 16 el acceso a la red local todavía es libre; este permiso lo habilitará más adelante.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES), 0)
        }
    }

    private fun avisarSiNoHayWifi() {
        val red = getSystemService(ConnectivityManager::class.java)
        val capacidades = red?.getNetworkCapabilities(red.activeNetwork)
        if (capacidades?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
            findViewById<TextView>(R.id.detalle_paso).setText(R.string.intercambio_sin_wifi)
        }
    }

    private fun soltarRed() {
        NfcEmparejamiento.dejarDeAnunciar()
        runCatching { NfcEmparejamiento.dejarDeLeer(this) }
        descubridor?.detener()
        descubridor = null
        anfitrion?.close()
        anfitrion = null
    }

    private fun soltarTodo() {
        trabajo?.cancel()
        trabajo = null
        soltarRed()
    }

    // --- Listas ---

    private inner class AdaptadorColeccion : BaseAdapter() {
        private val palabras = mutableListOf<Favorita>()
        private val elegidas = mutableSetOf<String>()

        fun cargar(nuevas: List<Favorita>) {
            palabras.clear()
            palabras.addAll(nuevas.sortedWith(OrdenAlfabetico.comparadorDePalabras))
            notifyDataSetChanged()
        }

        fun alternar(posicion: Int) {
            val palabra = palabras[posicion].palabra
            if (!elegidas.remove(palabra)) elegidas.add(palabra)
            notifyDataSetChanged()
            actualizarContador()
        }

        fun elegidas() = palabras.filter { it.palabra in elegidas }

        override fun getCount() = palabras.size
        override fun getItem(position: Int) = palabras[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val vista = convertView
                ?: LayoutInflater.from(parent.context).inflate(R.layout.item_intercambio_palabra, parent, false)
            val palabra = palabras[position]
            vista.findViewById<TextView>(R.id.item_palabra).text = palabra.palabra
            vista.findViewById<TextView>(R.id.item_definicion).text = palabra.definicion
            vista.findViewById<CheckBox>(R.id.item_elegida).isChecked = palabra.palabra in elegidas
            return vista
        }
    }

    private inner class AdaptadorVecinos : BaseAdapter() {
        private val vecinos = mutableListOf<Vecino>()

        fun agregar(vecino: Vecino) {
            if (vecinos.any { it.direccion == vecino.direccion && it.puerto == vecino.puerto }) return
            vecinos.add(vecino)
            notifyDataSetChanged()
            findViewById<TextView>(R.id.estado_busqueda).visibility = View.GONE
        }

        override fun getCount() = vecinos.size
        override fun getItem(position: Int) = vecinos[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val vista = convertView
                ?: LayoutInflater.from(parent.context).inflate(R.layout.item_vecino, parent, false)
            vista.findViewById<TextView>(R.id.item_vecino).text = vecinos[position].alias
            return vista
        }
    }

    private inner class AdaptadorOferta : BaseAdapter() {
        private val palabras = mutableListOf<PalabraOfrecida>()

        fun cargar(nuevas: List<PalabraOfrecida>) {
            palabras.clear()
            palabras.addAll(nuevas)
            notifyDataSetChanged()
        }

        override fun getCount() = palabras.size
        override fun getItem(position: Int) = palabras[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val vista = convertView
                ?: LayoutInflater.from(parent.context).inflate(R.layout.item_intercambio_palabra, parent, false)
            val palabra = palabras[position]
            vista.findViewById<TextView>(R.id.item_palabra).text = palabra.palabra
            vista.findViewById<TextView>(R.id.item_definicion).text = palabra.definicion
            vista.findViewById<CheckBox>(R.id.item_elegida).visibility = View.GONE
            return vista
        }
    }
}
