#!/usr/bin/env python3
"""Genera app/src/main/assets/databases/diccionario.db a partir de uno o más TSV.

Formato de entrada (UTF-8, tabuladores, sin cabecera; líneas con # se ignoran):
    palabra<TAB>categoria<TAB>definicion

Si una palabra aparece en varios TSV (o varias veces), gana la primera aparición: por eso la
semilla curada va antes que palabras_wikcionario.tsv (generado por importar_wikcionario.py).

El esquema replica exactamente la entidad Room `Palabra`; si cambias la entidad,
cambia también CREATE_TABLE y sube DiccionarioDatabase.VERSION y DB_VERSION.

Uso:
    python3 tools/build_db.py [--salida diccionario.db] [entrada.tsv ...]
"""
import argparse
import sqlite3
import sys
import unicodedata
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
ENTRADAS = [RAIZ / "tools" / "palabras_semilla.tsv", RAIZ / "tools" / "palabras_wikcionario.tsv"]
SALIDA = RAIZ / "app" / "src" / "main" / "assets" / "databases" / "diccionario.db"
DB_VERSION = 2  # debe coincidir con DiccionarioDatabase.VERSION

CREATE_TABLE = (
    "CREATE TABLE IF NOT EXISTS `palabras` ("
    "`id` INTEGER NOT NULL, `palabra` TEXT NOT NULL, `categoria` TEXT, "
    "`definicion` TEXT NOT NULL, PRIMARY KEY(`id`))"
)


def leer(ruta: Path, vistas: set):
    for n, linea in enumerate(ruta.read_text(encoding="utf-8").splitlines(), 1):
        if not linea.strip() or linea.startswith("#"):
            continue
        partes = [unicodedata.normalize("NFC", p.strip()) for p in linea.split("\t")]
        if len(partes) != 3 or not partes[0] or not partes[2]:
            sys.exit(f"{ruta}:{n}: se esperaban 3 columnas (palabra, categoria, definicion)")
        palabra, categoria, definicion = partes
        clave = palabra.lower()
        if clave in vistas:
            continue
        vistas.add(clave)
        yield palabra, categoria or None, definicion


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("entradas", nargs="*", type=Path)
    parser.add_argument("--salida", type=Path, default=SALIDA)
    args = parser.parse_args()
    entradas = args.entradas or [ruta for ruta in ENTRADAS if ruta.exists()]
    salida = args.salida

    vistas = set()
    filas = [fila for entrada in entradas for fila in leer(entrada, vistas)]
    salida.parent.mkdir(parents=True, exist_ok=True)
    salida.unlink(missing_ok=True)

    con = sqlite3.connect(salida)
    con.execute(CREATE_TABLE)
    # ids contiguos 1..N: la app elige al azar con MAX(id) sin recorrer la tabla.
    con.executemany(
        "INSERT INTO palabras (id, palabra, categoria, definicion) VALUES (?, ?, ?, ?)",
        ((i, *fila) for i, fila in enumerate(filas, 1)),
    )
    con.execute(f"PRAGMA user_version = {DB_VERSION}")
    con.commit()
    con.execute("VACUUM")
    con.close()
    print(f"{len(filas)} palabras -> {salida.relative_to(RAIZ) if salida.is_relative_to(RAIZ) else salida}")


if __name__ == "__main__":
    main()
