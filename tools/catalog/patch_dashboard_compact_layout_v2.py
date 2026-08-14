from pathlib import Path

path = Path(r"C:\Users\zomni\Projects\OpenCalori\app\src\main\java\com\opencalori\app\ui\dashboard\DashboardScreen.kt")
text = path.read_text(encoding="utf-8")

def remove_between(start: str, end: str) -> None:
    global text
    if text.count(start) != 1 or text.count(end) != 1:
        raise SystemExit(f"Expected unique boundaries: {start!r}")
    left = text.index(start)
    right = text.index(end, left)
    text = text[:left] + text[right:]

remove_between("        floatingActionButton = {", "    ) { padding ->")
old_padding = "contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp)"
if text.count(old_padding) != 1:
    raise SystemExit("Expected unique diary padding")
text = text.replace(old_padding, "contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)")

repeat_button_start = "                    TextButton(onClick = viewModel::requestRepeatPreviousDay) {"
repeat_button_end = "                    }\n"
if text.count(repeat_button_start) != 1:
    raise SystemExit("Expected unique repeat button")
button_start = text.index(repeat_button_start)
button_end = text.index(repeat_button_end, button_start) + len(repeat_button_end)
text = text[:button_start] + text[button_end:]

meal_tail = "                        onEditItem = { item -> editingItem = item }\n                    )\n                }\n"
if text.count(meal_tail) != 1:
    raise SystemExit("Expected unique meal item tail")
inline_add = '''                item {
                    Button(
                        onClick = { addFoodSheetVisible = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("\u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u0435\u0434\u0443")
                    }
                }
'''
text = text.replace(meal_tail, meal_tail + inline_add)

repeat_dialog_start = "    state.pendingRepeat?.let { preview ->"
repeat_dialog_marker = "@Composable\nprivate fun AddFoodBottomSheet("
if text.count(repeat_dialog_start) != 1 or text.count(repeat_dialog_marker) != 1:
    raise SystemExit("Expected unique repeat dialog")
dialog_start = text.index(repeat_dialog_start)
dialog_marker = text.index(repeat_dialog_marker, dialog_start)
text = text[:dialog_start] + "}\n" + text[dialog_marker:]

path.write_text(text, encoding="utf-8")
print("Updated dashboard composition")
