# Palabra del momento

App Android con un widget que muestra una palabra en español y su definición. La palabra cambia cada
vez que apagas la pantalla, así que al volver a encenderla ya tienes una nueva esperando.

## Funciones

- **Widget** translúcido para la pantalla de inicio, con colores dinámicos del sistema. Al tocarlo
  muestra otra palabra.
- **Cambio al apagar la pantalla** mediante un servicio en primer plano.
- **Notificación con la palabra**, que también se ve en la pantalla de bloqueo. Se puede reducir a
  una notificación mínima desde la app.
- **Sin repeticiones**: las palabras salen como un mazo barajado y no se repite ninguna hasta haberlas
  mostrado todas.
- **56 404 palabras** de [Wikcionario](https://es.wiktionary.org), sin vulgarismos ni términos
  despectivos.
- **Ayuda integrada** (botón `?`) con el estado de cada requisito, y botón para detener la app.

## Requisitos

- Android 8.0 (API 26) o superior. La app apunta a Android 16 (API 36).
- Para compilar: JDK 17 o superior y el Android SDK con la plataforma 36. El proyecto usa el wrapper
  de Gradle 9.7.1 y AGP 9.4.

## Compilar e instalar

```bash
# Indica dónde está el SDK (o crea local.properties con sdk.dir=/ruta/al/sdk)
export ANDROID_HOME=/ruta/al/Android/Sdk

./gradlew assembleDebug        # APK en app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # tests unitarios
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

También se puede abrir la carpeta con Android Studio y ejecutar desde ahí.

## APK de release firmada

La APK de release está optimizada con R8 (unos 5,8 MB frente a 8,6 MB de la de depuración) y va
firmada con una clave propia. Es la que conviene compartir.

### 1. Crear el keystore (solo la primera vez)

Guárdalo **fuera del proyecto**:

```bash
mkdir -p ~/keystores && chmod 700 ~/keystores
keytool -genkeypair -v -keystore ~/keystores/widget-diccionario-release.jks -storetype PKCS12 \
  -alias palabra-del-momento -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Tu nombre, O=Palabra del momento"
```

### 2. Configurar la firma

Crea `keystore.properties` en la raíz del proyecto. El archivo está en `.gitignore`, igual que
`*.jks` y `*.keystore`.

```properties
storeFile=/home/usuario/keystores/widget-diccionario-release.jks
storePassword=la-contraseña-del-keystore
keyAlias=palabra-del-momento
keyPassword=la-contraseña-del-keystore
```

En un keystore PKCS12 la contraseña de la clave es la misma que la del keystore. Protege el archivo
con `chmod 600 keystore.properties`.

Si `keystore.properties` no existe, la build de release no falla: genera
`app-release-unsigned.apk`, que no se puede instalar.

### 3. Compilar

```bash
./gradlew assembleRelease      # APK en app/build/outputs/apk/release/app-release.apk
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk   # comprobar la firma
```

### Antes de publicar una versión nueva

- **Sube `versionCode`** (y si quieres `versionName`) en `app/build.gradle.kts`. Android no instala
  una actualización con un `versionCode` igual o menor.
- **Firma siempre con el mismo keystore.** Una APK firmada con otra clave no se instala encima de la
  anterior: hay que desinstalar, y se pierden los ajustes. Por lo mismo, la release y la build de
  depuración no se pueden instalar una encima de la otra.

> ⚠️ **Respalda el keystore y `keystore.properties` fuera de la PC** (gestor de contraseñas, pendrive).
> Si se pierden, no se pueden publicar actualizaciones de la app ya instalada.

## Configuración en el teléfono

Para que todo funcione, en la app:

1. Activa **«Cambiar la palabra al apagar la pantalla»** y acepta el permiso de notificaciones.
2. Coloca el widget: mantén pulsado un espacio vacío de la pantalla de inicio → Widgets →
   Palabra del momento.
3. Pon la batería de la app en **Sin restricciones** (Ajustes → Aplicaciones → Palabra del momento →
   Batería). Si no, algunos fabricantes detienen la app tras unos días sin abrirla.
4. Para ver la palabra en la pantalla de bloqueo, las notificaciones del bloqueo tienen que mostrar
   contenido. En Samsung: Ajustes → Pantalla de bloqueo y AOD → Notificaciones → estilo «Detalles».

El botón `?` de la app muestra cuáles de estos puntos están activos.

## Cómo funciona

- **Pantalla apagada.** Android solo entrega `ACTION_SCREEN_OFF` a receptores registrados en tiempo
  de ejecución, así que `PantallaService` es un servicio en primer plano (`specialUse`) que mantiene
  el receptor vivo. Android exige que ese servicio muestre una notificación; la app la aprovecha para
  enseñar la palabra.
- **Arranque.** El servicio se reactiva al reiniciar el teléfono, al actualizar la app, al abrirla y
  al tocar el widget. Android 12+ no permite iniciarlo desde segundo plano en otros casos, por
  ejemplo al colocar el widget.
- **Mazo sin repeticiones.** `Mazo` es una permutación pseudoaleatoria de los ids (red de Feistel con
  clave aleatoria y *cycle walking*). Solo se guardan la clave y la posición, sin importar el tamaño
  del diccionario.
- **Base de datos.** SQLite de solo lectura con Room, precargada desde
  `app/src/main/assets/databases/diccionario.db`.

## Limitaciones conocidas

- **Widget en la pantalla de bloqueo.** El widget declara la categoría `keyguard`, pero en One UI
  (Samsung) la pantalla de bloqueo solo admite widgets de apps de Samsung. La notificación con la
  palabra es la alternativa.
- **Notificación.** Mientras la palabra cambia al apagar la pantalla, Android obliga a mostrar una
  notificación. Para que no haya ninguna, desactiva ese interruptor: el widget pasará a cambiar al
  tocarlo o cada 30 minutos.

## Estructura

```
app/src/main/java/app/widgetdiccionario/
├── MainActivity.kt              Pantalla principal, ayuda y botón de detener
├── Ajustes.kt                   Preferencias y estado del mazo
├── data/                        Room (Palabra, PalabraDao, DiccionarioDatabase) y Mazo
├── pantalla/                    PantallaService, NotificacionPalabra, ArranqueReceiver
└── widget/                      PalabraWidgetProvider y ActualizadorWidget
app/src/test/                    Tests del mazo
tools/
├── importar_wikcionario.py      Wikcionario (kaikki.org) → palabras_wikcionario.tsv
├── build_db.py                  TSV → diccionario.db
├── generar_icono.py             Glifo de una tipografía → icono de la app y de la notificación
├── palabras_semilla.tsv         Palabras seleccionadas a mano (tienen prioridad)
└── palabras_wikcionario.tsv     Palabras importadas de Wikcionario
```

## Actualizar el diccionario

```bash
python3 tools/importar_wikcionario.py   # descarga ~1,4 GB en streaming y genera el TSV
python3 tools/build_db.py               # combina semilla + Wikcionario en diccionario.db
```

Después sube `DiccionarioDatabase.VERSION` y `DB_VERSION` en `tools/build_db.py`, que tienen que
coincidir, para que Room reemplace la base ya instalada. Si una palabra aparece en varios TSV, gana
la primera aparición. Para agregar palabras propias, edita `tools/palabras_semilla.tsv`
(`palabra<TAB>categoría<TAB>definición`).

## Cambiar el icono

```bash
pip install fonttools          # en Arch: sudo pacman -S python-fonttools
python3 tools/generar_icono.py --fondo "#1F1B16" --tinta "#F3E3C3" --caracter "&"
```

Opciones: `--fuente` elige otra tipografía y `--vista-previa icono.png` genera un PNG para ver el
resultado (requiere `cairosvg`).

## Créditos y licencias

- **Definiciones:** [Wikcionario](https://es.wiktionary.org), bajo
  [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/), extraídas con
  [Wiktextract](https://kaikki.org). `tools/palabras_wikcionario.tsv` y `diccionario.db` heredan esa
  licencia.
- **Icono:** glifo de [Noto Serif](https://fonts.google.com/noto/specimen/Noto+Serif), bajo la
  SIL Open Font License.
- **Código:** todavía no tiene una licencia definida.
