from __future__ import annotations

import json
import zipfile
from pathlib import Path

archive = Path("tools/catalog/source/FoodData_Central_survey_food_json_2024-10-31.zip")
with zipfile.ZipFile(archive) as zf:
    with zf.open("surveyDownload.json") as source:
        payload = json.load(source)

print("root_keys=", sorted(payload.keys()))
for key, value in payload.items():
    if isinstance(value, list):
        print(f"list_key={key} count={len(value)}")
        if value:
            print("sample_keys=", sorted(value[0].keys()))
            print("sample_description=", value[0].get("description"))
    else:
        print(f"scalar_key={key} type={type(value).__name__}")
