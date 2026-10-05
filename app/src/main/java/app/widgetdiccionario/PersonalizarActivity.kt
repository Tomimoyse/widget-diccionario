package app.widgetdiccionario

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View

/** Lo que se puede cambiar de aspecto: el estilo del widget y cómo llevarlo a la pantalla de bloqueo. */
class PersonalizarActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personalizar)
        findViewById<View>(R.id.fila_estilo).setOnClickListener {
            startActivity(Intent(this, EstiloWidgetActivity::class.java))
        }
        findViewById<View>(R.id.fila_tutorial).setOnClickListener {
            startActivity(Intent(this, PantallaBloqueoActivity::class.java))
        }
    }
}
