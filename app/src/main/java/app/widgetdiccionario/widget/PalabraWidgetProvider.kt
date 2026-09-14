package app.widgetdiccionario.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import app.widgetdiccionario.pantalla.PantallaService

class PalabraWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACCION_REFRESCAR) {
            lanzarAsync { ActualizadorWidget.mostrarPalabraNueva(context) }
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        lanzarAsync { ActualizadorWidget.mostrarPalabraNueva(context) }
    }

    override fun onEnabled(context: Context) {
        PantallaService.iniciarSiCorresponde(context)
    }

    override fun onDisabled(context: Context) {
        // Sin widgets el servicio puede seguir haciendo falta para la notificación con palabra.
        if (!PantallaService.esNecesario(context)) PantallaService.detener(context)
    }

    companion object {
        const val ACCION_REFRESCAR = "app.widgetdiccionario.REFRESCAR"
    }
}
