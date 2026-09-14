#!/usr/bin/env python3
"""Genera el icono de la app (y el de la notificación) a partir de un glifo de una tipografía.

Crea en app/src/main/res:
  drawable/ic_launcher_foreground.xml, drawable/ic_launcher_monochrome.xml  (icono adaptativo)
  mipmap-anydpi/ic_launcher.xml, mipmap-anydpi/ic_launcher_round.xml
  values/ic_launcher.xml                                                    (color de fondo)
  drawable/ic_notificacion.xml                                              (silueta blanca)

Requiere fontTools (pip install fonttools). Con --vista-previa también cairosvg.

Uso:
    python3 tools/generar_icono.py [--fondo "#1F1B16"] [--tinta "#F3E3C3"]
        [--fuente /ruta/fuente.ttf] [--caracter "&"] [--vista-previa icono.png]
"""
import argparse
from pathlib import Path

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

RES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
FUENTE = "/usr/share/fonts/noto/NotoSerif-Italic.ttf"  # Noto Serif: SIL Open Font License

# Icono adaptativo: lienzo de 108 dp del que el launcher muestra solo los 72 dp centrales
# (y garantiza un círculo de 66 dp). 44 dp de glifo no se recorta con ninguna máscara.
LIENZO_ICONO, CAJA_ICONO = 108.0, 44.0
LIENZO_NOTIF, CAJA_NOTIF = 24.0, 20.0

VECTOR = """<?xml version="1.0" encoding="utf-8"?>
<!-- Glifo "{caracter}" de {fuente}, generado por tools/generar_icono.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{lienzo}dp"
    android:height="{lienzo}dp"
    android:viewportWidth="{lienzo}"
    android:viewportHeight="{lienzo}">
    <path
        android:fillColor="{color}"
        android:pathData="{path}" />
</vector>
"""

ADAPTATIVO = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_fondo" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""


def path_glifo(fuente: str, caracter: str, caja: float, lienzo: float) -> str:
    """Contorno del glifo como pathData, escalado a [caja] y centrado en un lienzo cuadrado."""
    font = TTFont(fuente)
    glifos = font.getGlyphSet()
    nombre = font.getBestCmap()[ord(caracter)]
    limites = BoundsPen(glifos)
    glifos[nombre].draw(limites)
    x0, y0, x1, y1 = limites.bounds
    escala = caja / max(x1 - x0, y1 - y0)
    # Centrar e invertir el eje Y (en la fuente crece hacia arriba).
    dx = (lienzo - (x1 - x0) * escala) / 2 - x0 * escala
    dy = (lienzo + (y1 - y0) * escala) / 2 + y0 * escala
    pen = SVGPathPen(glifos, lambda v: f"{v:.2f}")
    glifos[nombre].draw(TransformPen(pen, (escala, 0, 0, -escala, dx, dy)))
    return pen.getCommands()


def fmt(valor: float) -> str:
    return f"{valor:g}"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--fondo", default="#1F1B16", help="color de fondo del icono")
    parser.add_argument("--tinta", default="#F3E3C3", help="color del glifo")
    parser.add_argument("--fuente", default=FUENTE, help="archivo .ttf/.otf")
    parser.add_argument("--caracter", default="&")
    parser.add_argument("--vista-previa", type=Path, help="PNG con el icono bajo máscara circular y redondeada")
    args = parser.parse_args()

    nombre_fuente = Path(args.fuente).stem
    icono = path_glifo(args.fuente, args.caracter, CAJA_ICONO, LIENZO_ICONO)
    notif = path_glifo(args.fuente, args.caracter, CAJA_NOTIF, LIENZO_NOTIF)

    def vector(path, lienzo, color):
        return VECTOR.format(caracter=args.caracter, fuente=nombre_fuente, lienzo=fmt(lienzo), color=color, path=path)

    archivos = {
        "drawable/ic_launcher_foreground.xml": vector(icono, LIENZO_ICONO, args.tinta),
        "drawable/ic_launcher_monochrome.xml": vector(icono, LIENZO_ICONO, "#FF000000"),
        "drawable/ic_notificacion.xml": vector(notif, LIENZO_NOTIF, "#FFFFFFFF"),
        "mipmap-anydpi/ic_launcher.xml": ADAPTATIVO,
        "mipmap-anydpi/ic_launcher_round.xml": ADAPTATIVO,
        "values/ic_launcher.xml": (
            '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
            f'    <color name="ic_launcher_fondo">{args.fondo}</color>\n</resources>\n'
        ),
    }
    for ruta, contenido in archivos.items():
        destino = RES / ruta
        destino.parent.mkdir(parents=True, exist_ok=True)
        destino.write_text(contenido, encoding="utf-8")
        print(f"escrito {destino.relative_to(RES.parent.parent.parent.parent)}")

    if args.vista_previa:
        import cairosvg

        celdas = []
        mascaras = ('<circle cx="54" cy="54" r="36"/>', '<rect x="18" y="18" width="72" height="72" rx="22"/>')
        for i, mascara in enumerate(mascaras):
            celdas.append(
                f'<g transform="translate({i * 90 - 9},-9)"><clipPath id="m{i}">{mascara}</clipPath>'
                f'<g clip-path="url(#m{i})"><rect width="108" height="108" fill="{args.fondo}"/>'
                f'<path d="{icono}" fill="{args.tinta}"/></g></g>'
            )
        celdas.append(
            f'<g transform="translate(190,30)"><rect width="30" height="30" rx="4" fill="#555"/>'
            f'<g transform="translate(3,3)"><path d="{notif}" fill="#fff"/></g></g>'
        )
        svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="240" height="90" style="background:#ddd">{"".join(celdas)}</svg>'
        cairosvg.svg2png(bytestring=svg.encode(), write_to=str(args.vista_previa), output_width=720)
        print(f"vista previa -> {args.vista_previa}")


if __name__ == "__main__":
    main()
