from __future__ import annotations

import hashlib
import json
import math
import os
import sqlite3
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSET_DB = ROOT / "app" / "src" / "main" / "assets" / "databases" / "dishes_catalog.db"
MANIFEST = ROOT / "app" / "src" / "main" / "assets" / "databases" / "dish_catalog_manifest.json"
MINIMUM_DISHES = 1000
DELIMITER = "|"
PENDING_HASH = "pending-room-identity-hash"

@dataclass(frozen=True)
class Profile:
    calories: float
    protein: float
    fat: float
    carbs: float
    portion: float
    ingredients: tuple[str, ...]

@dataclass(frozen=True)
class DishRow:
    dish_id: int
    name: str
    aliases: str
    ingredients: str
    portion: float
    calories: float
    protein: float
    fat: float
    carbs: float

PROFILES = {
    "breakfast": Profile(158, 7.5, 6.5, 17, 250, ("крупа", "молоко", "фрукты")),
    "soup": Profile(60, 3, 3, 8, 350, ("бульон", "овощи", "основной ингредиент")),
    "salad": Profile(130, 7, 9, 7, 220, ("овощи", "белковый ингредиент", "заправка")),
    "pasta": Profile(170, 7, 6, 25, 250, ("крупа или паста", "соус", "овощи")),
    "meat": Profile(210, 16, 13, 8, 250, ("мясо", "гарнир", "овощи")),
    "fish": Profile(170, 18, 9, 7, 240, ("рыба или морепродукты", "гарнир", "овощи")),
    "dough": Profile(245, 9, 11, 30, 200, ("тесто", "начинка", "соус")),
    "world": Profile(190, 9, 9, 22, 250, ("основной ингредиент", "овощи", "соус")),
    "veggie": Profile(110, 4, 5, 15, 220, ("овощи", "крупа или бобовые", "соус")),
    "sweet": Profile(300, 5, 14, 40, 120, ("мука или основа", "молочный ингредиент", "подсластитель")),
}

VARIANTS = {
    "breakfast": (("классический", 1.00), ("домашний", 1.06), ("с ягодами", 0.94), ("с фруктами", 0.97), ("без сахара", 0.84)),
    "soup": (("классический", 1.00), ("домашний", 1.06), ("постный", 0.78), ("с мясом", 1.25), ("со сметаной", 1.15)),
    "salad": (("классический", 1.00), ("домашний", 1.08), ("лёгкий", 0.76), ("с курицей", 1.15), ("семейный", 1.05)),
    "pasta": (("классический", 1.00), ("домашний", 1.08), ("с овощами", 0.86), ("с курицей", 1.13), ("сливочный", 1.18)),
    "meat": (("классический", 1.00), ("домашний", 1.06), ("с овощами", 0.88), ("на гриле", 0.92), ("лёгкий", 0.80)),
    "fish": (("классический", 1.00), ("домашний", 1.05), ("на гриле", 0.88), ("с овощами", 0.84), ("запечённый", 0.96)),
    "dough": (("классический", 1.00), ("домашний", 1.08), ("с сыром", 1.18), ("с курицей", 1.10), ("овощной", 0.84)),
    "world": (("классический", 1.00), ("домашний", 1.05), ("с овощами", 0.86), ("с курицей", 1.12), ("острый", 1.01)),
    "veggie": (("классический", 1.00), ("домашний", 1.05), ("с овощами", 0.90), ("запечённый", 0.96), ("лёгкий", 0.75)),
    "sweet": (("классический", 1.00), ("домашний", 1.06), ("с ягодами", 0.92), ("шоколадный", 1.16), ("маленькая порция", 0.78)),
}

GROUPS = {
    "breakfast": [
        "Овсяная каша", "Гречневая каша", "Рисовая каша", "Пшённая каша", "Манная каша", "Кукурузная каша", "Булгур на молоке", "Творожная запеканка", "Сырники", "Ленивые вареники", "Омлет", "Яичница", "Яйца пашот", "Скрэмбл", "Блины", "Оладьи", "Вафли", "Французские тосты", "Гранола", "Мюсли", "Йогурт с фруктами", "Тост с авокадо", "Тост с лососем"
    ],
    "soup": [
        "Борщ", "Щи", "Рассольник", "Солянка", "Окрошка", "Свекольник", "Уха", "Куриный суп с лапшой", "Гороховый суп", "Грибной суп", "Томатный суп", "Тыквенный крем-суп", "Чечевичный суп", "Минестроне", "Фо бо", "Рамен", "Мисо-суп", "Харчо", "Шурпа", "Лагман", "Гаспачо", "Суп-пюре из брокколи", "Сырный суп", "Крем-суп из шампиньонов"
    ],
    "salad": [
        "Оливье", "Винегрет", "Сельдь под шубой", "Цезарь", "Греческий салат", "Крабовый салат", "Мимоза", "Капрезе", "Коул слоу", "Нисуаз", "Табуле", "Фаттуш", "Салат с тунцом", "Салат с лососем", "Салат с креветками", "Салат с киноа", "Салат с авокадо", "Овощной салат", "Салат из капусты", "Салат из свёклы", "Салат из огурцов и помидоров", "Салат из рукколы", "Салат с фасолью", "Салат с куриной грудкой", "Салат с индейкой"
    ],
    "pasta": [
        "Паста Карбонара", "Паста Болоньезе", "Паста Альфредо", "Паста Песто", "Паста с морепродуктами", "Паста с грибами", "Лазанья", "Ризотто", "Паэлья", "Плов", "Гречка с грибами", "Гречка с курицей", "Рис с овощами", "Рис с курицей", "Перловка с овощами", "Булгур с овощами", "Кускус с овощами", "Киноа с овощами", "Каша полба", "Макароны с сыром", "Макароны по-флотски", "Спагетти с томатным соусом", "Ньокки", "Равиоли", "Тортеллини", "Паста с тунцом"
    ],
    "meat": [
        "Котлеты", "Тефтели", "Фрикадельки", "Голубцы", "Перец фаршированный", "Жаркое", "Гуляш", "Бефстроганов", "Стейк из говядины", "Стейк из свинины", "Куриная грудка", "Куриные бёдра", "Куриные крылья", "Индейка запечённая", "Тушёная говядина", "Свинина запечённая", "Баранина с овощами", "Шашлык из свинины", "Шашлык из курицы", "Кебаб", "Люля-кебаб", "Чили кон карне", "Мясо по-французски", "Курица терияки"
    ],
    "fish": [
        "Лосось запечённый", "Форель на гриле", "Треска запечённая", "Минтай тушёный", "Скумбрия запечённая", "Сельдь с картофелем", "Тунец на гриле", "Рыбные котлеты", "Креветки с овощами", "Кальмары тушёные", "Мидии в соусе", "Осьминог на гриле", "Суши", "Роллы Филадельфия", "Роллы Калифорния", "Поке с лососем", "Поке с тунцом", "Том ям с креветками", "Рыбный бургер"
    ],
    "dough": [
        "Пельмени", "Вареники с картофелем", "Вареники с творогом", "Манты", "Хинкали", "Чебуреки", "Самса", "Беляши", "Пирожки с капустой", "Пирожки с мясом", "Кулебяка", "Хачапури", "Лепёшка с сыром", "Пицца Маргарита", "Пицца Пепперони", "Пицца четыре сыра", "Кесадилья", "Буррито", "Тако", "Шаурма", "Донер кебаб", "Хот-дог", "Гамбургер", "Чизбургер", "Клаб-сэндвич"
    ],
    "world": [
        "Азу по-татарски", "Бешбармак", "Долма", "Курица карри", "Тикка масала", "Бирьяни", "Дал", "Фалафель", "Хумус с питой", "Мусака", "Сувлаки", "Энчилада", "Начос", "Фахитас", "Кимчи-чиге", "Бибимбап", "Токпокки", "Удон", "Соба", "Вок с курицей", "Вок с говядиной", "Димсам", "Пад тай", "Курица кунг пао"
    ],
    "veggie": [
        "Овощное рагу", "Рататуй", "Баклажаны на гриле", "Кабачки запечённые", "Брокколи на пару", "Цветная капуста запечённая", "Картофельное пюре", "Картофель по-деревенски", "Драники", "Овощные котлеты", "Фасоль тушёная", "Нут с овощами", "Чечевица с овощами", "Тофу с овощами", "Грибное рагу", "Шампиньоны на гриле", "Кукуруза на гриле", "Тыква запечённая", "Батат запечённый", "Спаржа на гриле"
    ],
    "sweet": [
        "Чизкейк", "Тирамису", "Медовик", "Наполеон", "Шарлотка", "Брауни", "Панна-котта", "Эклер", "Круассан", "Сырный пончик", "Мороженое", "Фруктовый салат", "Йогуртовый десерт", "Творожный десерт", "Протеиновый батончик"
    ],
}

EXTRA_ALIASES = {
    "Борщ": ("борщ украинский", "borscht", "borshch"),
    "Оливье": ("салат оливье", "russian salad"),
    "Паста Карбонара": ("карбонара", "спагетти карбонара", "carbonara", "spaghetti carbonara"),
    "Паста Болоньезе": ("болоньезе", "spaghetti bolognese", "bolognese"),
    "Плов": ("узбекский плов", "pilaf", "plov"),
    "Пельмени": ("dumplings", "русские пельмени"),
    "Шаурма": ("шаверма", "донер", "shawarma", "kebab wrap"),
    "Суши": ("sushi", "нигири"),
    "Пицца Маргарита": ("маргарита", "pizza margherita"),
    "Роллы Филадельфия": ("филадельфия", "philadelphia roll"),
    "Курица карри": ("chicken curry", "карри"),
    "Рамен": ("ramen", "японский рамен"),
    "Фо бо": ("pho bo", "pho", "вьетнамский суп"),
    "Гамбургер": ("burger", "бургер"),
}

def compact(value: float) -> float:
    return round(value, 1)

def is_valid(row: DishRow) -> bool:
    values = (row.calories, row.protein, row.fat, row.carbs, row.portion)
    return all(math.isfinite(value) and value >= 0 for value in values) and row.calories <= 950 and row.protein <= 100 and row.fat <= 100 and row.carbs <= 100 and row.portion > 0

def aliases_for(base: str, visible: str) -> str:
    values = [visible, base, base.replace("ё", "е"), *EXTRA_ALIASES.get(base, ())]
    seen: set[str] = set()
    return DELIMITER.join(value for value in values if value and not (value.casefold() in seen or seen.add(value.casefold())))

def build_rows() -> list[DishRow]:
    rows: list[DishRow] = []
    next_id = 1
    for group, names in GROUPS.items():
        profile = PROFILES[group]
        for base in names:
            for label, factor in VARIANTS[group]:
                visible = base if label == "классический" else f"{base}, {label}"
                row = DishRow(
                    dish_id=next_id,
                    name=visible,
                    aliases=aliases_for(base, visible),
                    ingredients=DELIMITER.join(profile.ingredients),
                    portion=compact(profile.portion),
                    calories=compact(profile.calories * factor),
                    protein=compact(profile.protein * factor),
                    fat=compact(profile.fat * factor),
                    carbs=compact(profile.carbs * factor),
                )
                if not is_valid(row):
                    raise ValueError(f"Invalid dish row: {row}")
                rows.append(row)
                next_id += 1
    names = [row.name.casefold() for row in rows]
    if len(rows) < MINIMUM_DISHES or len(names) != len(set(names)):
        raise RuntimeError(f"Dish catalogue quality gate failed: {len(rows)} rows, {len(set(names))} unique names")
    return rows

def schema(connection: sqlite3.Connection, identity_hash: str) -> None:
    connection.execute("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
    connection.execute("INSERT INTO room_master_table (id, identity_hash) VALUES (42, ?)", (identity_hash,))
    connection.execute("""CREATE TABLE dishes (
        id INTEGER NOT NULL PRIMARY KEY,
        name TEXT NOT NULL,
        aliases TEXT NOT NULL,
        ingredients TEXT NOT NULL,
        portionGrams REAL NOT NULL,
        caloriesPer100g REAL NOT NULL,
        proteinPer100g REAL NOT NULL,
        fatPer100g REAL NOT NULL,
        carbsPer100g REAL NOT NULL
    )""")
    connection.execute("CREATE VIRTUAL TABLE dishes_fts USING fts4(name, aliases, content=dishes, tokenize=unicode61)")

def rebuild(identity_hash: str = PENDING_HASH) -> list[DishRow]:
    rows = build_rows()
    ASSET_DB.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=ASSET_DB.parent) as directory:
        candidate = Path(directory) / "dishes_catalog.db"
        connection = sqlite3.connect(candidate)
        try:
            schema(connection, identity_hash)
            connection.executemany(
                "INSERT INTO dishes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                [(row.dish_id, row.name, row.aliases, row.ingredients, row.portion, row.calories, row.protein, row.fat, row.carbs) for row in rows],
            )
            connection.execute("INSERT INTO dishes_fts(docid, name, aliases) SELECT id, name, aliases FROM dishes")
            connection.commit()
        finally:
            connection.close()
        os.replace(candidate, ASSET_DB)
    return rows

def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()

def write_manifest(rows: list[DishRow], identity_hash: str) -> None:
    payload = {
        "catalog_version": "2026.08.v1",
        "source": "OpenCalori curated common-dish catalogue",
        "method": "recipe-family serving profiles with deterministic local macro estimates",
        "dishes": len(rows),
        "minimum_dishes": MINIMUM_DISHES,
        "aliases": sum(len(row.aliases.split(DELIMITER)) for row in rows),
        "identity_hash": identity_hash,
        "sha256": sha256(ASSET_DB),
    }
    MANIFEST.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

def replace_identity_hash(identity_hash: str) -> None:
    if not ASSET_DB.exists():
        raise FileNotFoundError(ASSET_DB)
    with sqlite3.connect(ASSET_DB) as connection:
        connection.execute("UPDATE room_master_table SET identity_hash = ? WHERE id = 42", (identity_hash,))
        connection.commit()
    write_manifest(build_rows(), identity_hash)

if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--identity-hash":
        replace_identity_hash(sys.argv[2])
        print(f"Updated Room identity hash: {sys.argv[2]}")
    else:
        rows = rebuild()
        write_manifest(rows, PENDING_HASH)
        print(f"Built {len(rows)} dishes at {ASSET_DB}")
