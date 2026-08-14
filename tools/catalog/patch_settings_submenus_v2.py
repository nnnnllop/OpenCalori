from pathlib import Path

path = Path(r"C:\Users\zomni\Projects\OpenCalori\app\src\main\java\com\opencalori\app\ui\settings\SettingsScreen.kt")
text = path.read_text(encoding="utf-8")

def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one replacement anchor, got {count}: {old[:72]!r}")
    text = text.replace(old, new, 1)

replace_once(
    "    var analysisOptionsExpanded by remember { mutableStateOf(false) }",
    "    var analysisOptionsExpanded by remember { mutableStateOf(false) }\n"
    "    var page by remember { mutableStateOf(SettingsPage.ROOT) }",
)
replace_once('                title = { Text("Настройки") }', '                title = { Text(page.title) }')
replace_once(
    "                    IconButton(onClick = onBack) {",
    "                    IconButton(onClick = {\n"
    "                        if (page == SettingsPage.ROOT) onBack() else page = SettingsPage.ROOT\n"
    "                    }) {",
)
replace_once(
    "            // ---- Profile ----",
    "            if (page == SettingsPage.ROOT) {\n"
    "                SettingsMenuPage(onSelect = { page = it })\n"
    "            }\n"
    "            if (page == SettingsPage.PROFILE) {\n"
    "            // ---- Profile ----",
)
replace_once(
    "            // ---- AI ----",
    "            }\n"
    "            if (page == SettingsPage.AI) {\n"
    "            // ---- AI ----",
)
replace_once(
    "            // ---- Backup ----",
    "            }\n"
    "            if (page == SettingsPage.DATA) {\n"
    "            // ---- Backup ----",
)
replace_once(
    "            // ---- Donate ----",
    "            }\n"
    "            if (page == SettingsPage.SUPPORT) {\n"
    "            // ---- Donate ----",
)
replace_once(
    "            Spacer(Modifier.height(16.dp))\n        }\n    }\n}\n",
    "            Spacer(Modifier.height(16.dp))\n"
    "            }\n"
    "        }\n"
    "    }\n"
    "}\n",
)

menu_code = r'''private enum class SettingsPage(val title: String) {
    ROOT("\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438"),
    PROFILE("\u041f\u0440\u043e\u0444\u0438\u043b\u044c"),
    AI("\u0418\u0418 \u0438 \u0440\u0430\u0441\u043f\u043e\u0437\u043d\u0430\u0432\u0430\u043d\u0438\u0435"),
    DATA("\u0414\u0430\u043d\u043d\u044b\u0435"),
    SUPPORT("\u041f\u043e\u0434\u0434\u0435\u0440\u0436\u043a\u0430")
}

@Composable
private fun SettingsMenuPage(onSelect: (SettingsPage) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsDestinationCard(
            title = "\u041f\u0440\u043e\u0444\u0438\u043b\u044c",
            subtitle = "\u0426\u0435\u043b\u044c, \u0432\u0435\u0441 \u0438 \u043f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u044b \u0440\u0430\u0441\u0447\u0451\u0442\u0430",
            onClick = { onSelect(SettingsPage.PROFILE) }
        )
        SettingsDestinationCard(
            title = "\u0418\u0418 \u0438 \u0440\u0430\u0441\u043f\u043e\u0437\u043d\u0430\u0432\u0430\u043d\u0438\u0435",
            subtitle = "\u0424\u043e\u0442\u043e\u0441\u043a\u0430\u043d\u0435\u0440, \u0438\u0441\u0442\u043e\u0447\u043d\u0438\u043a \u041a\u0411\u0416\u0423 \u0438 \u043a\u043b\u044e\u0447 API",
            onClick = { onSelect(SettingsPage.AI) }
        )
        SettingsDestinationCard(
            title = "\u0414\u0430\u043d\u043d\u044b\u0435",
            subtitle = "\u042d\u043a\u0441\u043f\u043e\u0440\u0442, \u0438\u043c\u043f\u043e\u0440\u0442 \u0438 \u0432\u043e\u0441\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u0435",
            onClick = { onSelect(SettingsPage.DATA) }
        )
        SettingsDestinationCard(
            title = "\u041f\u043e\u0434\u0434\u0435\u0440\u0436\u043a\u0430 \u0438 \u043e \u043f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u0438",
            subtitle = "Telegram-\u043a\u0430\u043d\u0430\u043b, \u0438\u0441\u0445\u043e\u0434\u043d\u044b\u0439 \u043a\u043e\u0434 \u0438 \u0432\u0435\u0440\u0441\u0438\u044f",
            onClick = { onSelect(SettingsPage.SUPPORT) }
        )
    }
}

@Composable
private fun SettingsDestinationCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = AppShapes.Medium) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

'''
replace_once("private const val TELEGRAM_URL", menu_code + "private const val TELEGRAM_URL")
path.write_text(text, encoding="utf-8")
print("Updated settings submenu navigation")
