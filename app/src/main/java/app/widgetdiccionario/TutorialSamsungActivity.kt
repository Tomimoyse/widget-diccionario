package app.widgetdiccionario

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.net.toUri

/**
 * Tutorial para poner el widget en la pantalla de bloqueo de Samsung, que solo admite widgets propios,
 * usando Good Lock y su módulo LockStar. Para modelos sin Good Lock sugiere FineLock (no oficial).
 */
class TutorialSamsungActivity : Activity() {

    private data class Paso(@StringRes val titulo: Int, @StringRes val texto: Int, @DrawableRes val imagen: Int, val anchoDp: Int)

    private val pasos = listOf(
        Paso(R.string.tutorial_paso1_titulo, R.string.tutorial_paso1_texto, R.drawable.tutorial_1_galaxy_store, 96),
        Paso(R.string.tutorial_paso2_titulo, R.string.tutorial_paso2_texto, R.drawable.tutorial_2_good_lock, 240),
        Paso(R.string.tutorial_paso3_titulo, R.string.tutorial_paso3_texto, R.drawable.tutorial_3_lockstar, 240),
        Paso(R.string.tutorial_paso4_titulo, R.string.tutorial_paso4_texto, R.drawable.tutorial_4_fijar_widget, 240),
        Paso(R.string.tutorial_paso5_titulo, R.string.tutorial_paso5_texto, R.drawable.tutorial_5_resultado, 280),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial_samsung)

        val contenedor = findViewById<LinearLayout>(R.id.pasos)
        pasos.forEachIndexed { indice, paso ->
            val vista = LayoutInflater.from(this).inflate(R.layout.item_paso_tutorial, contenedor, false)
            vista.findViewById<TextView>(R.id.paso_numero).text = getString(R.string.numero_orden, indice + 1)
            vista.findViewById<TextView>(R.id.paso_titulo).setText(paso.titulo)
            vista.findViewById<TextView>(R.id.paso_texto).setText(paso.texto)
            vista.findViewById<ImageView>(R.id.paso_imagen).apply {
                setImageResource(paso.imagen)
                contentDescription = getString(paso.titulo)
                layoutParams.width = (paso.anchoDp * resources.displayMetrics.density).toInt()
                clipToOutline = true // redondea la captura con la forma del marco
            }
            contenedor.addView(vista)
        }

        findViewById<View>(R.id.boton_galaxy_store).setOnClickListener { abrirGoodLock() }
        findViewById<ImageView>(R.id.imagen_finelock).clipToOutline = true
        findViewById<View>(R.id.boton_finelock).setOnClickListener { buscarFineLock() }
    }

    /** Abre la ficha de Good Lock en Galaxy Store; si la tienda no está, en su versión web. */
    private fun abrirGoodLock() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, "samsungapps://ProductDetail/$PAQUETE_GOOD_LOCK".toUri()))
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent(Intent.ACTION_VIEW, "https://galaxystore.samsung.com/detail/$PAQUETE_GOOD_LOCK".toUri()),
            )
        }
    }

    /**
     * Busca FineLock en Play Store. Se usa la búsqueda y no un id de paquete para no enviar al usuario a
     * una app equivocada.
     */
    private fun buscarFineLock() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, "market://search?q=FineLock&c=apps".toUri()))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/search?q=FineLock&c=apps".toUri()))
        }
    }

    private companion object {
        const val PAQUETE_GOOD_LOCK = "com.samsung.android.goodlock"
    }
}
