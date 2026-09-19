# Polimatía

App Android con un widget que muestra una palabra en español y su definición. La palabra cambia cada
vez que apagas la pantalla, así que al volver a encenderla ya tienes una nueva esperando.

## Funciones

- **Widget** translúcido para la pantalla de inicio, con colores dinámicos del sistema. Al tocarlo
  muestra otra palabra.
- **Colección**: la estrella del widget guarda la palabra que estás viendo, y la app tiene una
  sección con todas las guardadas, en orden alfabético español, agrupadas por letra inicial y con
  buscador (sin importar tildes ni mayúsculas).
- **Estilo del widget** configurable: color y transparencia del fondo, color y tamaño de la letra,
  con vista previa en vivo. Los cambios se aplican al tocar «Confirmar».
- **Pantalla de bloqueo**: la app pregunta la marca del celular. En Samsung explica cómo poner el
  widget con Good Lock y LockStar (o FineLock, no oficial, en modelos más viejos); en otras marcas,
  qué activar para ver la palabra con la notificación.
- Interfaz de estilo editorial (papel y tinta, tipografía serif), con variante oscura.
- **Cambio al apagar la pantalla** mediante un servicio en primer plano.
- **Notificación con la palabra**, que también se ve en la pantalla de bloqueo. Se puede reducir a
  una notificación mínima desde la app.
- **Sin repeticiones**: las palabras salen como un mazo barajado y no se repite ninguna hasta haberlas
  mostrado todas.
- **56 404 palabras** de [Wikcionario](https://es.wiktionary.org), sin vulgarismos ni términos
  despectivos.
- **Intercambio de palabras**, de dos maneras: por la **red local**, si los dos están en la misma
  Wi-Fi (se encuentran y se conectan solos, o con un código), o **acercando los teléfonos por NFC**,
  que no necesita red alguna. Cada uno elige **una** palabra y acepta la que recibe; las recibidas
  quedan anotadas con el alias de quien las envió.
- **Botón para detener la app** por completo.

## Requisitos

- Android 8.0 (API 26) o superior. La app apunta a Android 16 (API 36).
- Para compilar: JDK 17 o superior y el Android SDK con la plataforma 36. El proyecto usa el wrapper
  de Gradle 9.7.1 y AGP 9.4.

## Compilar e instalar

```bash
# Indica dónde está el SDK (o crea local.properties con sdk.dir=/ruta/al/sdk)
export ANDROID_HOME=/ruta/al/Android/Sdk

./gradlew assembleDebug        # APK en app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # tests (mazo y pantallas, con Robolectric)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

También se puede abrir la carpeta con Android Studio y ejecutar desde ahí.

## APK de release firmada

La APK de release está optimizada con R8 (bastante más liviana que la de depuración) y va
firmada con una clave propia. Es la que conviene compartir.

### 1. Crear el keystore (solo la primera vez)

Guárdalo **fuera del proyecto**:

```bash
mkdir -p ~/keystores && chmod 700 ~/keystores
keytool -genkeypair -v -keystore ~/keystores/widget-diccionario-release.jks -storetype PKCS12 \
  -alias palabra-del-momento -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Tu nombre, O=Polimatía"
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
   Polimatía.
3. Pon la batería de la app en **Sin restricciones** (Ajustes → Aplicaciones → Polimatía →
   Batería). Si no, algunos fabricantes detienen la app tras unos días sin abrirla.
4. Para ver la palabra en la pantalla de bloqueo, las notificaciones del bloqueo tienen que mostrar
   contenido. En Samsung: Ajustes → Pantalla de bloqueo y AOD → Notificaciones → estilo «Detalles».

En la app, Personalizar → Widget en la pantalla de bloqueo → Otra marca muestra cuáles de estos
puntos están activos.

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
- **Intercambio por la red.** Solo dentro de la red local: la app rechaza cualquier dirección que no
  sea privada y ata los sockets a la red Wi-Fi, porque si el teléfono tiene los datos móviles como red
  por defecto la conexión saldría por ahí. Los teléfonos se anuncian con NSD (mDNS) y hablan por un
  socket TCP con un protocolo de texto propio, una línea por mensaje. El anfitrión genera un código de
  6 dígitos que ambos ven y tienen que confirmar. Los dos se descubren a la vez, así que para no
  cruzarse conecta el de la dirección «menor» y el otro espera. Todo vive mientras la pantalla está
  abierta: al salir se cierran el anuncio y el puerto.
- **Intercambio por NFC.** La palabra entera viaja en el toque, sin red: un teléfono hace de tarjeta
  (`HostApduService`) y el otro de lector, porque Android Beam ya no existe. El lector entrega su
  palabra en trozos y se lleva la de la tarjeta en la misma operación, así que un solo acercamiento
  completa el intercambio y no hace falta confirmar ningún código. Mientras la pantalla está al
  frente, la app se declara servicio NFC preferente (`CardEmulation.setPreferredService`); sin eso
  Android pregunta con qué app atender el toque.
- **Colección.** Va en una base aparte (`favoritas.db`) y cada palabra guarda su propia copia del
  texto, la categoría y la definición. La base del diccionario se reemplaza entera al actualizarlo, y
  los ids cambian; así la colección no se pierde. El orden alfabético se calcula en la app: SQLite
  ordena por bytes y mandaría «árbol» o «ñandú» al final.

## Limitaciones conocidas

- **Widget en la pantalla de bloqueo.** El widget declara la categoría `keyguard`, pero en One UI
  (Samsung) la pantalla de bloqueo solo admite widgets de apps de Samsung. Se puede poner con
  Good Lock y LockStar (la app incluye un tutorial); en modelos que no admiten Good Lock existe
  FineLock, que no es oficial. En cualquier marca queda la notificación con la palabra.
- **Intercambio.** Por la red necesita que las dos personas estén en la misma Wi-Fi; algunas redes
  (de invitados, públicas o con «aislamiento de clientes») bloquean el descubrimiento e incluso la
  conexión directa, y para esos casos están el código y el NFC. El NFC exige que los dos teléfonos lo
  tengan, encendido y con la pantalla desbloqueada. En Android 16 el acceso a la red local todavía es libre,
  pero el permiso `NEARBY_WIFI_DEVICES` lo gobernará en versiones futuras: la app ya lo pide.
- **Notificación.** Mientras la palabra cambia al apagar la pantalla, Android obliga a mostrar una
  notificación. Para que no haya ninguna, desactiva ese interruptor: el widget pasará a cambiar al
  tocarlo o cada 30 minutos.

## Estructura

```
app/src/main/java/app/widgetdiccionario/
├── MainActivity.kt              Pantalla principal y botón de detener
├── ColeccionActivity.kt         Colección de palabras guardadas
├── EstiloWidgetActivity.kt      Personalización del widget con vista previa
├── PantallaBloqueoActivity.kt   Pregunta la marca del celular
├── TutorialSamsungActivity.kt   Tutorial de Good Lock, LockStar y FineLock
├── OtraMarcaActivity.kt         Requisitos para otras marcas, con su estado
├── Ajustes.kt                   Preferencias y estado del mazo
├── IntercambioActivity.kt       Intercambio de palabras entre dos teléfonos
├── data/                        Room (diccionario y colección), OrdenAlfabetico y Mazo
├── intercambio/                 Protocolo, sockets, NSD y emparejamiento por NFC
├── pantalla/                    PantallaService, NotificacionPalabra, ArranqueReceiver
└── widget/                      PalabraWidgetProvider, ActualizadorWidget y EstiloWidget
app/src/test/                    Tests del mazo, la colección y las pantallas (Robolectric)
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
coincidir, para que Room reemplace la base ya instalada. Las favoritas no se ven afectadas.

Si una palabra aparece en varios TSV, gana la primera aparición. Para agregar palabras propias, edita
`tools/palabras_semilla.tsv` (`palabra<TAB>categoría<TAB>definición`).

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
- **Tipografías:** [Playfair Display](https://fonts.google.com/specimen/Playfair+Display) (sin
  modificar) y [Noto Serif](https://fonts.google.com/noto/specimen/Noto+Serif) (reducida a
  caracteres latinos), ambas bajo la SIL Open Font License. Las licencias van en
  `app/src/main/assets/licencias/`.
- **Icono:** glifo de Noto Serif.
- **Estrellas:** iconos de Material Symbols, bajo la licencia Apache 2.0.
- **Código:** todavía no tiene una licencia definida.
