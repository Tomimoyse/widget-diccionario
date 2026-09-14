package app.widgetdiccionario

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.text.Html
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import app.widgetdiccionario.pantalla.NotificacionPalabra
import app.widgetdiccionario.pantalla.PantallaService
import app.widgetdiccionario.widget.ActualizadorWidget
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {

    private companion object {
        /** Margen para que el sistema procese la detención del servicio antes de matar el proceso. */
        const val RETARDO_CIERRE_MS = 500L
    }

    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val vistaPalabra = findViewById<TextView>(R.id.vista_palabra)
        val vistaDefinicion = findViewById<TextView>(R.id.vista_definicion)
        val switchPantalla = findViewById<Switch>(R.id.switch_pantalla)
        val switchNotificacion = findViewById<Switch>(R.id.switch_notificacion)
        val descNotificacion = findViewById<TextView>(R.id.desc_notificacion)

        fun mostrarOtra() = scope.launch {
            val palabra = ActualizadorWidget.siguientePalabra(this@MainActivity) ?: return@launch
            vistaPalabra.text = listOfNotNull(palabra.palabra, palabra.categoria).joinToString("  ")
            vistaDefinicion.text = palabra.definicion
            ActualizadorWidget.mostrar(this@MainActivity, palabra)
        }

        // La notificación solo existe mientras el servicio está activo.
        fun habilitarNotificacion(habilitado: Boolean) {
            switchNotificacion.isEnabled = habilitado
            descNotificacion.isEnabled = habilitado
        }

        findViewById<Button>(R.id.boton_nueva).setOnClickListener { mostrarOtra() }
        findViewById<Button>(R.id.boton_ayuda).setOnClickListener { mostrarAyuda() }
        findViewById<Button>(R.id.boton_detener).setOnClickListener { confirmarDetener() }
        mostrarOtra()

        switchPantalla.isChecked = Ajustes.actualizarAlApagar(this)
        habilitarNotificacion(switchPantalla.isChecked)
        switchPantalla.setOnCheckedChangeListener { _, activado ->
            Ajustes.setActualizarAlApagar(this, activado)
            habilitarNotificacion(activado)
            if (activado && Ajustes.mostrarNotificacion(this)) pedirPermisoNotificaciones()
            aplicarEstadoServicio()
            scope.launch { ActualizadorWidget.repintar(this@MainActivity) }
        }

        switchNotificacion.isChecked = Ajustes.mostrarNotificacion(this)
        switchNotificacion.setOnCheckedChangeListener { _, activado ->
            Ajustes.setMostrarNotificacion(this, activado)
            if (activado) pedirPermisoNotificaciones()
            aplicarEstadoServicio()
            scope.launch {
                NotificacionPalabra.refrescar(this@MainActivity, ActualizadorWidget.palabraActual(this@MainActivity))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Desde una Activity visible el arranque en primer plano siempre está permitido.
        PantallaService.iniciarSiCorresponde(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun aplicarEstadoServicio() {
        if (PantallaService.esNecesario(this)) {
            PantallaService.iniciarSiCorresponde(this)
        } else {
            PantallaService.detener(this)
        }
    }

    private fun confirmarDetener() {
        AlertDialog.Builder(this)
            .setTitle(R.string.detener_titulo)
            .setMessage(R.string.detener_mensaje)
            .setPositiveButton(R.string.detener_confirmar) { _, _ -> detenerApp() }
            .setNegativeButton(R.string.detener_cancelar, null)
            .show()
    }

    /**
     * Equivalente a "forzar detención" sin tocar los ajustes. El servicio se detiene antes de matar el
     * proceso: si solo se matara, Android lo reiniciaría por ser START_STICKY.
     */
    private fun detenerApp() {
        PantallaService.detener(this)
        // Cerrar la tarea dispara onPause/onStop, que vuelcan a disco las SharedPreferences pendientes.
        finishAndRemoveTask()
        Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, RETARDO_CIERRE_MS)
    }

    /** Lista de requisitos con su estado actual (lo que se puede comprobar desde la app). */
    private fun mostrarAyuda() {
        fun estado(ok: Boolean) = getString(if (ok) R.string.ayuda_ok else R.string.ayuda_revisar)
        val info = getString(R.string.ayuda_info)
        val bateriaSinRestricciones = getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(packageName)

        val puntos = listOf(
            getString(R.string.ayuda_pantalla, estado(Ajustes.actualizarAlApagar(this))),
            getString(R.string.ayuda_permiso, estado(NotificacionPalabra.tienePermiso(this))),
            getString(R.string.ayuda_bateria, estado(bateriaSinRestricciones)),
            getString(R.string.ayuda_widget, estado(ActualizadorWidget.idsWidgets(this).isNotEmpty())),
            getString(R.string.ayuda_bloqueo, info),
            getString(R.string.ayuda_reactivar, info),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.ayuda_titulo)
            .setMessage(Html.fromHtml(puntos.joinToString("<br><br>"), Html.FROM_HTML_MODE_COMPACT))
            .setPositiveButton(R.string.ayuda_cerrar, null)
            .setNeutralButton(R.string.ayuda_abrir_ajustes) { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                )
            }
            .show()
    }

    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }
}
