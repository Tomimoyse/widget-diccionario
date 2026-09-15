package app.widgetdiccionario

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View

/** Pregunta la marca del celular y lleva a las instrucciones que corresponden. */
class PantallaBloqueoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantalla_bloqueo)
        findViewById<View>(R.id.fila_samsung).setOnClickListener {
            startActivity(Intent(this, TutorialSamsungActivity::class.java))
        }
        findViewById<View>(R.id.fila_otra).setOnClickListener {
            startActivity(Intent(this, OtraMarcaActivity::class.java))
        }
    }
}
