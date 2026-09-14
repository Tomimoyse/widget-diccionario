#!/usr/bin/env python3
"""Convierte el volcado de Wikcionario en español (extraído por Wiktextract, kaikki.org) al TSV
que consume build_db.py: palabra<TAB>categoria<TAB>definicion.

Las definiciones de Wikcionario están bajo licencia CC BY-SA 4.0: la base de datos generada hereda
esa licencia y la app debe atribuir la fuente.

Uso:
    python3 tools/importar_wikcionario.py [URL_o_ruta.jsonl] [salida.tsv]
"""
import json
import re
import sys
import urllib.request
from collections import Counter
from pathlib import Path

URL = "https://kaikki.org/eswiktionary/Espa%C3%B1ol/kaikki.org-dictionary-Espa%C3%B1ol.jsonl"
SALIDA = Path(__file__).resolve().parent / "palabras_wikcionario.tsv"

# pos_title de Wikcionario -> abreviatura. Lo que no esté aquí (formas flexivas, nombres propios,
# siglas, locuciones, refranes, afijos...) se descarta.
CATEGORIAS = {
    "Sustantivo masculino": "m.",
    "Sustantivo femenino": "f.",
    "Sustantivo masculino y femenino": "m. y f.",
    "Sustantivo femenino y masculino": "m. y f.",
    "Sustantivo ambiguo": "amb.",
    "Adjetivo": "adj.",
    "Verbo transitivo": "tr.",
    "Verbo intransitivo": "intr.",
    "Verbo pronominal": "prnl.",
    "Verbo": "v.",
    "Adverbio": "adv.",
    "Interjección": "interj.",
}
# Palabras que no queremos en una pantalla de bloqueo, aunque solo una acepción esté marcada.
ETIQUETAS_PALABRA_EXCLUIDA = {"vulgar", "derogatory", "offensive", "pejorative"}
# Acepciones que no sirven como definición principal.
ETIQUETAS_ACEPCION_EXCLUIDA = {"form-of", "obsolete", "outdated", "archaic", "rare", "slang", "Lunfardo"}
PALABRA_VALIDA = re.compile(r"^[a-zñáéíóúü]{3,}$")
GLOSA_REMISION = re.compile(
    r"^(forma|plural|femenino|masculino|variante|grafía|ortografía|participio|gerundio|abreviatura|"
    r"símbolo|diminutivo|aumentativo|superlativo|véase|ver|apócope|contracción|sinónimo|alternativa)\b",
    re.IGNORECASE,
)
# Glosas que solo remiten a otra entrada, p. ej. "Compadecer (uso pronominal de ...)".
GLOSA_INCOMPLETA = re.compile(r"\(uso pronominal de|\.\.\.\)?\.?$")


def abrir(origen: str):
    if origen.startswith(("http://", "https://")):
        return urllib.request.urlopen(origen)
    return open(origen, "rb")


def categoria(pos_title: str):
    if pos_title in CATEGORIAS:
        return CATEGORIAS[pos_title]
    for prefijo in ("Adjetivo", "Adverbio", "Verbo"):
        if pos_title.startswith(prefijo + " ") and not pos_title.startswith("Verbo auxiliar"):
            return CATEGORIAS[prefijo] if prefijo != "Verbo" else "v."
    return None


def definicion(entrada: dict):
    for acepcion in entrada.get("senses", []):
        if ETIQUETAS_ACEPCION_EXCLUIDA & set(acepcion.get("tags", [])):
            continue
        glosa = " ".join(acepcion.get("glosses", [])).strip()
        glosa = re.sub(r"\s+", " ", glosa)
        if (
            not 12 <= len(glosa) <= 280
            or GLOSA_REMISION.match(glosa)
            or GLOSA_INCOMPLETA.search(glosa)
            or "\t" in glosa
        ):
            continue
        if not glosa.endswith((".", "!", "?", ")")):
            glosa += "."
        return glosa[0].upper() + glosa[1:]
    return None


def main():
    origen = sys.argv[1] if len(sys.argv) > 1 else URL
    salida = Path(sys.argv[2]) if len(sys.argv) > 2 else SALIDA
    elegidas, excluidas, motivos = {}, set(), Counter()

    with abrir(origen) as flujo:
        for n, linea in enumerate(flujo, 1):
            entrada = json.loads(linea)
            palabra = entrada.get("word", "")
            if entrada.get("lang_code") != "es" or not PALABRA_VALIDA.match(palabra):
                motivos["no es palabra válida"] += 1
                continue
            etiquetas = {t for s in entrada.get("senses", []) for t in s.get("tags", [])}
            if ETIQUETAS_PALABRA_EXCLUIDA & etiquetas:
                excluidas.add(palabra)
                motivos["vulgar/despectiva"] += 1
                continue
            abreviatura = categoria(entrada.get("pos_title", ""))
            if abreviatura is None:
                motivos["categoría descartada"] += 1
                continue
            if palabra in elegidas:
                continue
            glosa = definicion(entrada)
            if glosa is None:
                motivos["sin definición útil"] += 1
                continue
            elegidas[palabra] = (abreviatura, glosa)
            if n % 100_000 == 0:
                print(f"{n:,} entradas leídas, {len(elegidas):,} palabras", file=sys.stderr, flush=True)

    filas = sorted((p, c, d) for p, (c, d) in elegidas.items() if p not in excluidas)
    with open(salida, "w", encoding="utf-8") as f:
        f.write("# Fuente: Wikcionario (es.wiktionary.org), CC BY-SA 4.0, vía kaikki.org/Wiktextract\n")
        for fila in filas:
            f.write("\t".join(fila) + "\n")
    print(f"{len(filas):,} palabras -> {salida}", file=sys.stderr)
    print("descartes:", dict(motivos), file=sys.stderr)


if __name__ == "__main__":
    main()
