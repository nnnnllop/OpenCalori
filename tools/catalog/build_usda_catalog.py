from __future__ import annotations

import hashlib
import json
import math
import os
import shutil
import sqlite3
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE_ZIP = ROOT / "tools" / "catalog" / "source" / "FoodData_Central_survey_food_json_2024-10-31.zip"
ASSET_DB = ROOT / "app" / "src" / "main" / "assets" / "databases" / "products.db"
MANIFEST = ROOT / "app" / "src" / "main" / "assets" / "databases" / "catalog_manifest.json"
JSON_MEMBER = "surveyDownload.json"
SOURCE_URL = "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_survey_food_json_2024-10-31.zip"
MINIMUM_PRODUCTS = 5000
NUTRIENT_NUMBERS = {
    "calories": "208",
    "protein": "203",
    "fat": "204",
    "carbs": "205",
}


@dataclass(frozen=True)
class ProductRow:
    product_id: int
    name: str
    calories: float
    protein: float
    fat: float
    carbs: float


def source_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def amount_by_nutrient_number(food: dict) -> dict[str, float]:
    values: dict[str, float] = {}
    for item in food.get("foodNutrients", []):
        nutrient = item.get("nutrient") or {}
        number = str(nutrient.get("number") or "")
        amount = item.get("amount")
        if amount is None:
            continue
        try:
            values[number] = float(amount)
        except (TypeError, ValueError):
            continue
    return values


def is_valid_energy(value: float) -> bool:
    return math.isfinite(value) and 0.0 <= value <= 950.0


def is_valid_macro(value: float) -> bool:
    return math.isfinite(value) and 0.0 <= value <= 100.0


def load_rows() -> list[ProductRow]:
    with zipfile.ZipFile(SOURCE_ZIP) as archive:
        with archive.open(JSON_MEMBER) as source:
            payload = json.load(source)
    rows: list[ProductRow] = []
    for food in payload.get("SurveyFoods", []):
        try:
            product_id = int(food["fdcId"])
        except (KeyError, TypeError, ValueError):
            continue
        name = " ".join(str(food.get("description") or "").split())
        values = amount_by_nutrient_number(food)
        if not name or any(number not in values for number in NUTRIENT_NUMBERS.values()):
            continue
        calories = values[NUTRIENT_NUMBERS["calories"]]
        protein = values[NUTRIENT_NUMBERS["protein"]]
        fat = values[NUTRIENT_NUMBERS["fat"]]
        carbs = values[NUTRIENT_NUMBERS["carbs"]]
        if not is_valid_energy(calories) or not all(is_valid_macro(value) for value in (protein, fat, carbs)):
            continue
        rows.append(ProductRow(product_id, name, calories, protein, fat, carbs))
    return merge_with_existing_rows(rows)


def merge_with_existing_rows(imported: list[ProductRow]) -> list[ProductRow]:
    with sqlite3.connect(ASSET_DB) as connection:
        legacy = [
            ProductRow(*row)
            for row in connection.execute(
                "SELECT id, name, caloriesPer100g, proteinPer100g, fatPer100g, carbsPer100g FROM products"
            )
        ]
    by_name: dict[str, ProductRow] = {}
    used_ids: set[int] = set()
    for row in legacy + imported:
        normalized = row.name.casefold()
        if normalized in by_name or row.product_id in used_ids:
            continue
        by_name[normalized] = row
        used_ids.add(row.product_id)
    return sorted(by_name.values(), key=lambda row: (row.name.casefold(), row.product_id))


def rebuild_database(rows: list[ProductRow]) -> None:
    if not ASSET_DB.exists():
        raise FileNotFoundError(f"Room seed database does not exist: {ASSET_DB}")
    ASSET_DB.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=ASSET_DB.parent) as temporary_directory:
        candidate = Path(temporary_directory) / "products.db"
        shutil.copy2(ASSET_DB, candidate)
        connection = sqlite3.connect(candidate)
        try:
            connection.execute("PRAGMA foreign_keys = ON")
            connection.execute("BEGIN IMMEDIATE")
            connection.execute("DELETE FROM products_fts")
            connection.execute("DELETE FROM products")
            connection.executemany(
                "INSERT INTO products (id, name, caloriesPer100g, proteinPer100g, fatPer100g, carbsPer100g) VALUES (?, ?, ?, ?, ?, ?)",
                [(row.product_id, row.name, row.calories, row.protein, row.fat, row.carbs) for row in rows],
            )
            connection.executemany(
                "INSERT INTO products_fts (docid, name) VALUES (?, ?)",
                [(row.product_id, row.name) for row in rows],
            )
            product_count = connection.execute("SELECT COUNT(*) FROM products").fetchone()[0]
            fts_count = connection.execute("SELECT COUNT(*) FROM products_fts").fetchone()[0]
            if product_count != len(rows) or fts_count != len(rows):
                raise RuntimeError(f"seed count mismatch products={product_count} fts={fts_count} rows={len(rows)}")
            connection.commit()
        except BaseException:
            connection.rollback()
            raise
        finally:
            connection.close()
        # Windows can keep an asset file open for indexing even after readers close it.
        # The candidate has already passed count and FTS validation, so copying it here is safe
        # and avoids a rename that requires deleting the target path.
        shutil.copy2(candidate, ASSET_DB)


def write_manifest(rows: list[ProductRow]) -> None:
    manifest = {
        "schemaVersion": 1,
        "catalogVersion": "0.2.7-usda-fndds-2021-2023",
        "source": {
            "provider": "USDA FoodData Central",
            "dataset": "FNDDS 2021-2023",
            "sourceUrl": SOURCE_URL,
            "license": "CC0 1.0 / public domain",
            "sha256": source_sha256(SOURCE_ZIP),
        },
        "products": len(rows),
        "dishes": 0,
        "aliases": 0,
        "nutritionRule": "All values are per 100 g as published by the source.",
    }
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    if not SOURCE_ZIP.exists():
        raise FileNotFoundError(f"Download the documented source first: {SOURCE_ZIP}")
    rows = load_rows()
    if len(rows) < MINIMUM_PRODUCTS:
        raise RuntimeError(f"Expected at least {MINIMUM_PRODUCTS} complete products, received {len(rows)}")
    rebuild_database(rows)
    write_manifest(rows)
    print(f"Built offline product catalogue: {len(rows)} products")


if __name__ == "__main__":
    main()
