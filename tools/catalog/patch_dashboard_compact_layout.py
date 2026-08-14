from pathlib import Path

path = Path(r"C:\Users\zomni\Projects\OpenCalori\app\src\main\java\com\opencalori\app\ui\dashboard\DashboardScreen.kt")
text = path.read_text(encoding="utf-8")

fab_start = "        floatingActionButton = {"
fab_end = "    ) { padding ->"
if text.count(fab_start) != 1 or text.count(fab_end) != 1:
    raise SystemExit("Expected unique Scaffold FAB boundaries")
start = text.index(fab_start)
end = text.index(fab_end, start)
text = text[:start] + text[end:]

old_padding = "contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp)"
if text.count(old_padding) != 1:
    raise SystemExit("Expected unique diary content padding")
text = text.replace(
    old_padding,
    "contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)",
)

header_start = "            item {\n                Row(\n                    Modifier.fillMaxWidth(),\n                    horizontalArrangement = Arrangement.SpaceBetween,\n                    verticalAlignment = Alignment.CenterVertically\n                ) {"
header_end = "            if (state.meals.isEmpty()) {"
if text.count(header_start) != 1 or text.count(header_end) != 1:
    raise SystemExit("Expected unique diary header boundaries")
start = text.index(header_start)
end = text.index(header_end, start)
header = '''            item {
                Text(
                    "\\u041f\\u0440\\u0438\\u0451\\u043c\\u044b \u043f\\u0438\\u0449\\u0438",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
'''
text = text[:start] + header + text[end:]

items_start = "                items(state.meals, key = { it.id }) { meal ->"
items_end = "            }\n        }\n    }\n    if (addFoodSheetVisible) {"
if text.count(items_start) != 1 or text.count(items_end) != 1:
    raise SystemExit("Expected unique diary items boundaries")
start = text.index(items_start)
end = text.index(items_end, start)
items_block = text[start:end]
inline_add = '''                item {
                    Button(
                        onClick = { addFoodSheetVisible = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("\\u0414\\u043e\\u0431\\u0430\\u0432\\u0438\\u0442\\u044c \u0435\\u0434\\u0443")
                    }
                }
'''
text = text[:start] + items_block + inline_add + text[end:]

repeat_start = "    state.pendingRepeat?.let { preview ->"
repeat_end = "}\n@Composable\nprivate fun AddFoodBottomSheet("
if text.count(repeat_start) != 1 or text.count(repeat_end) != 1:
    raise SystemExit("Expected unique repeat-dialog boundaries")
start = text.index(repeat_start)
end = text.index(repeat_end, start)
text = text[:start] + text[end:]

path.write_text(text, encoding="utf-8")
print("Updated dashboard composition")
