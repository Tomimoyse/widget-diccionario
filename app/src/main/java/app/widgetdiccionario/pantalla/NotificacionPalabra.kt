package app.widgetdiccionario.pantalla

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.widgetdiccionario.Ajustes
import app.widgetdiccionario.MainActivity
import app.widgetdiccionario.R
import app.widgetdiccionario.data.Palabra

/**
 * Notificación del servicio en primer plano. Muestra la palabra actual para que también se vea en la
 * pantalla de bloqueo, incluso en launchers que no admiten widgets ahí (p. ej. One UI).
 *
 * Android no permite un servicio en primer plano sin notificación: si el usuario la desactiva en la app
 * ([Ajustes.mostrarNotificacion]) se publica una versión mínima, sin palabra y oculta en el bloqueo.
 */
object NotificacionPalabra {
    const val ID = 1
    private const val CANAL = "palabra_del_momento"
    private const val CANAL_MINIMO = "servicio_segundo_plano"
    private val CANALES_ANTIGUOS = listOf("escucha_pantalla", "palabra_actual")

    fun crearCanales(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // La importancia de un canal ya creado no se puede cambiar, así que los anteriores se descartan.
        CANALES_ANTIGUOS.forEach(manager::deleteNotificationChannel)
        // DEFAULT y no LOW/MIN: las notificaciones silenciosas no se muestran en la pantalla de bloqueo.
        // Sin sonido ni vibración, y con setOnlyAlertOnce, en la práctica no molesta al actualizarse.
        val canal = NotificationChannel(
            CANAL,
            context.getString(R.string.notif_canal),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_canal_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }
        val canalMinimo = NotificationChannel(
            CANAL_MINIMO,
            context.getString(R.string.notif_canal_minimo),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(R.string.notif_canal_minimo_desc)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
            setShowBadge(false)
        }
        manager.createNotificationChannels(listOf(canal, canalMinimo))
    }

    /** Sin [palabra] (antes de leer la base de datos) muestra un texto genérico. */
    fun construir(context: Context, palabra: Palabra?): Notification {
        val abrirApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (!Ajustes.mostrarNotificacion(context)) {
            return NotificationCompat.Builder(context, CANAL_MINIMO)
                .setSmallIcon(R.drawable.ic_notificacion)
                .setContentText(context.getString(R.string.notif_texto_minimo))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setSilent(true)
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(abrirApp)
                .build()
        }
        val builder = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(abrirApp)
        if (palabra == null) {
            builder.setContentText(context.getString(R.string.notif_texto))
        } else {
            val titulo = listOfNotNull(palabra.palabra, palabra.categoria?.takeIf { it.isNotBlank() })
                .joinToString(" · ")
            builder
                .setContentTitle(titulo)
                .setContentText(palabra.definicion)
                .setStyle(NotificationCompat.BigTextStyle().bigText(palabra.definicion))
        }
        return builder.build()
    }

    /** Refleja [palabra] en la notificación, si está activada la notificación con palabra. */
    fun actualizar(context: Context, palabra: Palabra) {
        if (Ajustes.mostrarNotificacion(context)) publicar(context, palabra)
    }

    /** Vuelve a publicar la notificación según los ajustes actuales (p. ej. al cambiar el interruptor). */
    fun refrescar(context: Context, palabra: Palabra?) = publicar(context, palabra)

    fun tienePermiso(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun publicar(context: Context, palabra: Palabra?) {
        // Solo mientras el servicio debe estar activo: fuera de él quedaría una notificación huérfana.
        if (!Ajustes.actualizarAlApagar(context)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(ID, construir(context, palabra))
    }
}
