package app.widgetdiccionario

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.RadioButton
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import app.widgetdiccionario.data.Favorita
import app.widgetdiccionario.data.Favoritas
import app.widgetdiccionario.data.OrdenAlfabetico
import app.widgetdiccionario.intercambio.Anfitrion
import app.widgetdiccionario.intercambio.Canal
import app.widgetdiccionario.intercambio.Descubridor
import app.widgetdiccionario.intercambio.IntercambioNfc
import app.widgetdiccionario.intercambio.PaqueteNfc
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

    /** Una vez que hay con quién hablar, se ignora cualquier otro teléfono que aparezca. */
    /** Lo que este teléfono entrega por NFC, y si ya empezó un intercambio (por red o por toque). */
    private var paqueteNfc: PaqueteNfc? = null
    private var intercambioEnCurso = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intercambio)
        findViewById<ListView>(R.id.lista_ofrecer).apply {
            adapter = adaptadorColeccion
            setOnItemClickListener { _, _, posicion, _ -> adaptadorColeccion.elegir(posicion) }
        }
        findViewById<EditText>(R.id.buscador_ofrecer).addTextChangedListener {
            adaptadorColeccion.buscar(it?.toString().orEmpty())
        }
        findViewById<ListView>(R.id.lista_vecinos).apply {
            adapter = adaptadorVecinos
            setOnItemClickListener { _, _, posicion, _ -> conectarCon(adaptadorVecinos.getItem(posicion)) }
        }
        findViewById<ListView>(R.id.lista_oferta).adapter = adaptadorOferta

        findViewById<Button>(R.id.boton_alias).setOnClickListener { guardarAlias() }
        findViewById<EditText>(R.id.campo_alias).setText(Ajustes.alias(this))
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

    override fun onResume() {
        super.onResume()
        IntercambioNfc.preferirNuestraTarjeta(this)
    }

    override fun onPause() {
        IntercambioNfc.dejarDePreferir(this)
        super.onPause()
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
        val elegida = adaptadorColeccion.elegida()
        findViewById<TextView>(R.id.contador_elegidas).text = elegida
            ?.let { getString(R.string.intercambio_elegida, it.palabra) }
            ?: getString(R.string.intercambio_sin_elegir)
        findViewById<Button>(R.id.boton_elegir).apply {
            isEnabled = elegida != null
            alpha = if (elegida != null) 1f else 0.4f
        }
    }

    // --- Emparejar ---

    private fun empezarAEmparejar() {
        irA(Paso.EMPAREJAR)
        val alias = Ajustes.alias(this)
        val ofrecida = adaptadorColeccion.elegida() ?: return
        // Por NFC viaja la palabra entera: ese camino no necesita Wi-Fi.
        prepararNfc(PaqueteNfc(alias, PalabraOfrecida(ofrecida.palabra, ofrecida.categoria, ofrecida.definicion)))

        pedirPermisoDeRedLocal()
        direccionPropia = RedLocal.direccionPropia(this)
        val propia = direccionPropia
        val servidor = if (propia == null) null else runCatching { Anfitrion() }.getOrNull()
        if (servidor == null || propia == null) {
            soloPorNfc()
            return
        }
        anfitrion = servidor
        findViewById<TextView>(R.id.tu_codigo).text =
            getString(R.string.intercambio_tu_codigo, RedLocal.codigoDeConexion(propia, servidor.puerto))

        Descubridor(this).also { descubridor = it }.apply {
            anunciarse(alias, servidor.puerto)
            buscar { vecino -> scope.launch { alEncontrar(vecino) } }
        }

        // Queda esperando a que el otro teléfono se conecte.
        trabajo = scope.launch {
            val canal = withContext(Dispatchers.IO) { runCatching { servidor.esperarConexion() }.getOrNull() }
            // Si nos acaban de leer por NFC, la conexión viene de ese toque y el código sobra.
            if (canal != null) intercambiar(canal, anfitrion = true)
        }
    }

    /** Este teléfono queda como "tarjeta"; el botón lo pasa a lector para leer al otro. */
    private fun prepararNfc(paquete: PaqueteNfc) {
        paqueteNfc = paquete
        if (!IntercambioNfc.disponible(this)) return
        IntercambioNfc.ofrecer(paquete) { recibido -> scope.launch { recibirPorNfc(recibido) } }
        findViewById<View>(R.id.aviso_nfc).visibility = View.VISIBLE
        findViewById<View>(R.id.boton_nfc).visibility = View.VISIBLE
    }

    /** Sin Wi-Fi el intercambio todavía es posible acercando los teléfonos. */
    private fun soloPorNfc() {
        findViewById<View>(R.id.tu_codigo).visibility = View.GONE
        findViewById<View>(R.id.aviso_nfc).visibility = View.GONE
        findViewById<View>(R.id.estado_busqueda).visibility = View.GONE
        findViewById<View>(R.id.boton_usar_codigo).visibility = View.GONE
        findViewById<TextView>(R.id.detalle_paso).setText(
            if (IntercambioNfc.disponible(this)) {
                R.string.intercambio_solo_nfc
            } else {
                R.string.intercambio_sin_wifi_ni_nfc
            },
        )
    }

    private fun leerPorNfc() {
        val paquete = paqueteNfc ?: return
        findViewById<TextView>(R.id.estado_busqueda).apply {
            visibility = View.VISIBLE
            setText(R.string.intercambio_nfc_esperando)
        }
        IntercambioNfc.leer(this, paquete) { recibido -> scope.launch { recibirPorNfc(recibido) } }
    }

    /** Llegó la palabra del otro por NFC: no hay más diálogo, solo aceptarla o no. */
    private suspend fun recibirPorNfc(recibido: PaqueteNfc) {
        if (intercambioEnCurso) return
        intercambioEnCurso = true
        IntercambioNfc.dejarDeLeer(this)
        soltarRed()
        mostrarOferta(recibido.alias, listOf(recibido.palabra))
        val acepto = esperarDecision()
        val guardadas = if (acepto) {
            withContext(Dispatchers.IO) {
                Favoritas.recibir(
                    this@IntercambioActivity,
                    listOf(Favorita(recibido.palabra.palabra, recibido.palabra.categoria, recibido.palabra.definicion, 0)),
                    de = recibido.alias,
                )
            }
        } else {
            0
        }
        mostrarFinal(recibido.alias, guardadas, recibido.palabra.palabra, acepto, elOtroAcepto = null)
    }

    private fun esUnoMismo(vecino: Vecino) =
        vecino.puerto == anfitrion?.puerto && vecino.direccion.hostAddress == direccionPropia?.hostAddress

    /** Aparece en la lista; el intercambio empieza cuando tocas su nombre. */
    private fun alEncontrar(vecino: Vecino) {
        if (esUnoMismo(vecino) || !adaptadorVecinos.agregar(vecino)) return
        findViewById<TextView>(R.id.estado_busqueda).setText(R.string.intercambio_elegir_persona)
    }

    private fun conectarCon(vecino: Vecino) {
        if (intercambioEnCurso) return
        findViewById<TextView>(R.id.estado_busqueda).text =
            getString(R.string.intercambio_conectando, vecino.alias)
        trabajo?.cancel()
        trabajo = scope.launch {
            val canal = withContext(Dispatchers.IO) {
                runCatching { Visitante.conectar(this@IntercambioActivity, vecino.direccion, vecino.puerto) }.getOrNull()
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
                runCatching { Visitante.conectar(this@IntercambioActivity, destino.first, destino.second) }.getOrNull()
            }
            if (canal == null) mostrarError(getString(R.string.intercambio_error, "")) else intercambiar(canal, false)
        }
    }

    // --- El intercambio ---

    private suspend fun intercambiar(canal: Canal, anfitrion: Boolean) {
        if (intercambioEnCurso) return
        intercambioEnCurso = true
        soltarRed()
        val sesion = Sesion(canal, anfitrion)
        val alias = Ajustes.alias(this)
        val ofrecidas = adaptadorColeccion.elegida()
            ?.let { listOf(PalabraOfrecida(it.palabra, it.categoria, it.definicion)) }
            .orEmpty()
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
                if (acepto) {
                    Favoritas.recibir(
                        this@IntercambioActivity,
                        oferta.map { Favorita(it.palabra, it.categoria, it.definicion, 0) },
                        de = saludo.alias,
                    )
                } else {
                    0
                }
            }
            val elOtroAcepto = withContext(Dispatchers.IO) { sesion.esperarRespuesta().also { sesion.despedirse() } }
            mostrarFinal(saludo.alias, guardadas, oferta.firstOrNull()?.palabra, acepto, elOtroAcepto)
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

    private suspend fun mostrarOferta(alias: String, palabras: List<PalabraOfrecida>) {
        encabezado(R.string.intercambio_oferta_titulo, "")
        findViewById<TextView>(R.id.titulo_paso).text = getString(R.string.intercambio_oferta_titulo, alias)
        val repetidas = palabras.filter { yaEstaEnLaColeccion(it) }.map { it.palabra }.toSet()
        adaptadorOferta.cargar(palabras, repetidas)
        irA(Paso.OFERTA)
    }

    private suspend fun yaEstaEnLaColeccion(palabra: PalabraOfrecida) = Favoritas.esFavorita(
        this,
        app.widgetdiccionario.data.Palabra(0, palabra.palabra, palabra.categoria, palabra.definicion),
    )

    private fun mostrarFinal(
        alias: String,
        guardadas: Int,
        palabraRecibida: String?,
        acepto: Boolean,
        elOtroAcepto: Boolean?,
    ) {
        encabezado(R.string.intercambio_final_titulo, "")
        val resumen = buildString {
            append(
                when {
                    guardadas > 0 && palabraRecibida != null ->
                        getString(R.string.intercambio_guardada, palabraRecibida, alias)
                    // Aceptada pero ya la tenía: no se pisa lo que ya estaba guardado.
                    acepto && palabraRecibida != null ->
                        getString(R.string.intercambio_ya_la_tenias, palabraRecibida)
                    else -> getString(R.string.intercambio_guardadas_ninguna)
                },
            )
            append("\n")
            append(
                when (elOtroAcepto) {
                    true -> getString(R.string.intercambio_el_otro_acepto, alias)
                    false -> getString(R.string.intercambio_el_otro_rechazo, alias)
                    // Por NFC no hay vuelta de respuesta: solo se sabe si se llevó la palabra.
                    null -> getString(R.string.intercambio_tu_palabra_viajo)
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

    /** Lo que importa es tener una dirección en la Wi-Fi, aunque la red por defecto sean los datos. */
    private fun avisarSiNoHayWifi() {
        if (RedLocal.redWifi(this) == null) {
            findViewById<TextView>(R.id.detalle_paso).setText(R.string.intercambio_sin_wifi)
        }
    }

    /**
     * Suelta lo de la red. La oferta por NFC se mantiene: el otro teléfono la recoge justo después de
     * entregarnos la suya, y borrarla aquí dejaba el toque a medias.
     */
    private fun soltarRed() {
        runCatching { IntercambioNfc.dejarDeLeer(this) }
        descubridor?.detener()
        descubridor = null
        anfitrion?.close()
        anfitrion = null
    }

    private fun soltarTodo() {
        trabajo?.cancel()
        trabajo = null
        soltarRed()
        IntercambioNfc.dejarDeOfrecer()
    }

    // --- Listas ---

    private inner class AdaptadorColeccion : BaseAdapter() {
        private val todas = mutableListOf<Favorita>()
        private var palabras = emptyList<Favorita>()
        private var elegida: String? = null

        fun cargar(nuevas: List<Favorita>) {
            todas.clear()
            todas.addAll(nuevas.sortedWith(OrdenAlfabetico.comparadorDePalabras))
            palabras = todas.toList()
            notifyDataSetChanged()
        }

        fun buscar(consulta: String) {
            palabras = todas.filter { OrdenAlfabetico.coincide(it, consulta) }
            notifyDataSetChanged()
        }

        /** Se ofrece una sola palabra por intercambio: elegir otra reemplaza a la anterior. */
        fun elegir(posicion: Int) {
            val palabra = palabras[posicion].palabra
            elegida = if (elegida == palabra) null else palabra
            notifyDataSetChanged()
            actualizarContador()
        }

        fun elegida() = todas.firstOrNull { it.palabra == elegida }

        override fun getCount() = palabras.size
        override fun getItem(position: Int) = palabras[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val vista = convertView
                ?: LayoutInflater.from(parent.context).inflate(R.layout.item_intercambio_palabra, parent, false)
            val palabra = palabras[position]
            vista.findViewById<TextView>(R.id.item_palabra).text = palabra.palabra
            vista.findViewById<TextView>(R.id.item_definicion).text = palabra.definicion
            vista.findViewById<RadioButton>(R.id.item_elegida).isChecked = palabra.palabra == elegida
            return vista
        }
    }

    private inner class AdaptadorVecinos : BaseAdapter() {
        private val vecinos = mutableListOf<Vecino>()

        /** False si ya estaba en la lista. */
        fun agregar(vecino: Vecino): Boolean {
            if (vecinos.any { it.direccion == vecino.direccion && it.puerto == vecino.puerto }) return false
            vecinos.add(vecino)
            notifyDataSetChanged()
            return true
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
        private var repetidas = emptySet<String>()

        fun cargar(nuevas: List<PalabraOfrecida>, yaGuardadas: Set<String>) {
            palabras.clear()
            palabras.addAll(nuevas)
            repetidas = yaGuardadas
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
            vista.findViewById<RadioButton>(R.id.item_elegida).visibility = View.GONE
            vista.findViewById<TextView>(R.id.item_repetida).visibility =
                if (palabra.palabra in repetidas) View.VISIBLE else View.GONE
            return vista
        }
    }
}
