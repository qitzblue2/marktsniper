package nl.marktsniper.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.marktsniper.*

@Composable
fun App() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Watch?>(null) }
    var filter by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                listOf(Triple("Vondsten", Icons.Default.Home, 0), Triple("Zoeken", Icons.Default.Search, 1),
                    Triple("Instellingen", Icons.Default.Settings, 2)).forEach { (label, icon, i) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, null) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Accent.copy(alpha = .18f),
                            selectedIconColor = Accent, selectedTextColor = Accent))
                }
            }
        },
        floatingActionButton = {
            if (tab != 2) ExtendedFloatingActionButton(
                onClick = { editing = Watch(name = "", query = "") },
                containerColor = Accent, contentColor = Color(0xFF1A0D05),
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nieuwe snipe", fontWeight = FontWeight.Bold) })
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HitsScreen(filter, onFilter = { filter = it })
                1 -> WatchesScreen(onEdit = { editing = it }, onOpen = { filter = it; tab = 0 }, onPreset = { editing = it })
                else -> SettingsScreen()
            }
        }
    }
    editing?.let { w -> WatchEditor(w, onDismiss = { editing = null }) }
}

@Composable
private fun Header(title: String, sub: String? = null) {
    Row(Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(AccentGradient), Alignment.Center) {
            Text("🎯", fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            if (sub != null) Text(sub, color = Mute, fontSize = 13.sp)
        }
        LiveDot()
    }
}

@Composable
private fun LiveDot() {
    val on by Store.running.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) Green else Mute))
        Spacer(Modifier.width(6.dp))
        Text(if (on) "Live" else "Gepauzeerd", color = Mute, fontSize = 13.sp)
    }
}

@Composable
fun HitsScreen(filter: Long?, onFilter: (Long?) -> Unit) {
    val hits by Store.hits.collectAsStateWithLifecycle()
    val watches by Store.watches.collectAsStateWithLifecycle()
    var cheapFirst by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val list = hits.filter { filter == null || it.watchId == filter }
        .let { if (cheapFirst) it.sortedBy { h -> h.price } else it }

    Column {
        Header("Vondsten", "${list.size} advertenties")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Chip("Alles", filter == null) { onFilter(null) } }
            items(watches, key = { it.id }) { w -> Chip(w.name, filter == w.id) { onFilter(w.id) } }
            item { Chip(if (cheapFirst) "💶 Goedkoopst" else "🕒 Nieuwst", false) { cheapFirst = !cheapFirst } }
        }
        if (list.isEmpty()) Empty(if (watches.isEmpty()) "Maak je eerste snipe aan.\nTik op ‘Nieuwe snipe’." else "Nog niks gevonden.\nWe blijven zoeken 👀")
        else LazyVerticalGrid(GridCells.Adaptive(160.dp), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.watchId.toString() + it.id }) { h -> HitCard(h) { open(ctx, h.url) } }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = if (selected) Accent else Panel2,
        border = BorderStroke(1.dp, if (selected) Accent else Line)) {
        Text(text, Modifier.padding(14.dp, 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) Color(0xFF1A0D05) else MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun HitCard(h: Hit, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = Panel, border = BorderStroke(1.dp, Line)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Panel2)) {
                AsyncImage(h.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Badge(platformLabel(h.platform), platformColor(h.platform), Modifier.align(Alignment.TopStart))
                if (h.notified && System.currentTimeMillis() - h.foundAt < 3_600_000)
                    Badge("NIEUW", Green, Modifier.align(Alignment.TopEnd), Color(0xFF03140C))
            }
            Column(Modifier.padding(12.dp)) {
                Text("€" + "%,.0f".format(h.price).replace(',', '.'), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(h.title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(h.location, color = Mute, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(ago(h.foundAt), color = Mute, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color, modifier: Modifier, textColor: Color = Color.White) {
    Text(text.uppercase(), modifier.padding(8.dp).clip(RoundedCornerShape(6.dp)).background(color).padding(7.dp, 3.dp),
        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor)
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔍", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(text, color = Mute, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun WatchesScreen(onEdit: (Watch) -> Unit, onOpen: (Long) -> Unit, onPreset: (Watch) -> Unit) {
    val watches by Store.watches.collectAsStateWithLifecycle()
    val hits by Store.hits.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item { Header("Snipes", "${watches.count { it.active }} actief") }
        item {
            Text("SNELSTART", Modifier.padding(20.dp, 12.dp, 20.dp, 8.dp), color = Mute, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PRESETS) { p -> Chip(p.name, false) { onPreset(p.copy(id = System.currentTimeMillis())) } }
            }
            Text("MIJN SNIPES", Modifier.padding(20.dp, 24.dp, 20.dp, 8.dp), color = Mute, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        }
        if (watches.isEmpty()) item { Text("Nog geen snipes. Kies een snelstart hierboven 👆", Modifier.padding(20.dp), color = Mute) }
        items(watches, key = { it.id }) { w ->
            val count = hits.count { it.watchId == w.id }
            Surface(onClick = { onOpen(w.id) }, modifier = Modifier.fillMaxWidth().padding(16.dp, 6.dp), shape = RoundedCornerShape(16.dp),
                color = Panel, border = BorderStroke(1.dp, Line)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (w.active) Color.Unspecified else Mute)
                        Text("€${w.minPrice} – €${w.maxPrice} · ${w.platforms.joinToString(" · ") { platformLabel(it) }}", color = Mute, fontSize = 12.5.sp)
                        if (w.lastCheck > 0) Text("Gecheckt ${ago(w.lastCheck)}", color = Mute, fontSize = 12.sp)
                        w.lastError?.let { Text("⚠ $it", color = Color(0xFFFF7A7A), fontSize = 12.sp, maxLines = 2) }
                    }
                    Text("$count", Modifier.clip(CircleShape).background(Panel2).padding(10.dp, 4.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Switch(w.active, { Store.saveWatch(w.copy(active = it), filtersChanged = false) }, Modifier.padding(start = 8.dp),
                        colors = SwitchDefaults.colors(checkedTrackColor = Accent))
                    IconButton({ onEdit(w) }) { Icon(Icons.Default.Edit, "Bewerken", tint = Mute) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WatchEditor(initial: Watch, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = Store.watches.value.none { it.id == initial.id }
    var name by remember { mutableStateOf(initial.name) }
    var query by remember { mutableStateOf(initial.query) }
    var include by remember { mutableStateOf(initial.include) }
    var exclude by remember { mutableStateOf(initial.exclude) }
    var range by remember { mutableStateOf(initial.minPrice.toFloat()..initial.maxPrice.toFloat()) }
    var platforms by remember { mutableStateOf(initial.platforms.toSet()) }
    var category by remember { mutableStateOf(CATEGORIES.first { it.id == initial.categoryId }) }
    var catOpen by remember { mutableStateOf(false) }
    val maxSlider = maxOf(2000f, initial.maxPrice.toFloat())

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp, 0.dp, 20.dp, 32.dp).imePadding()) {
            Text(if (isNew) "Nieuwe snipe" else "Snipe bewerken", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            Field("Naam", name, "bv. iPhone 14 koopje") { name = it }
            Field("Zoekterm", query, "iphone") { query = it }
            Field("Moet één van deze bevatten (komma's)", include, "iphone 13, iphone 14, iphone 15") { include = it }
            Field("Uitsluiten (komma's)", exclude, "hoesje, kapot, defect") { exclude = it }

            Spacer(Modifier.height(8.dp))
            Text("Prijs", color = Mute, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PriceBox(range.start.toInt(), Modifier.weight(1f)) { range = it.toFloat().coerceAtMost(range.endInclusive)..range.endInclusive }
                Text("  –  ", color = Mute)
                PriceBox(range.endInclusive.toInt(), Modifier.weight(1f)) { range = range.start..it.toFloat().coerceAtLeast(range.start) }
            }
            RangeSlider(range, { range = it.start.roundTo5()..it.endInclusive.roundTo5() }, valueRange = 0f..maxSlider,
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Line))

            Text("Platforms", color = Mute, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ALL_PLATFORMS.forEach { p ->
                    val on = p in platforms
                    Surface(onClick = { platforms = if (on) platforms - p else platforms + p }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp), color = if (on) platformColor(p).copy(alpha = .18f) else Bg,
                        border = BorderStroke(1.5.dp, if (on) platformColor(p) else Line)) {
                        Text(platformLabel(p), Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            ExposedDropdownMenuBox(catOpen, { catOpen = it }) {
                OutlinedTextField(category.name, {}, readOnly = true, label = { Text("Categorie (Marktplaats/2dehands)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catOpen) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                ExposedDropdownMenu(catOpen, { catOpen = false }) {
                    CATEGORIES.forEach { c -> DropdownMenuItem({ Text(c.name) }, { category = c; catOpen = false }) }
                }
            }

            Spacer(Modifier.height(20.dp))
            val valid = name.isNotBlank() && query.isNotBlank() && platforms.isNotEmpty()
            Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp))
                .background(if (valid) AccentGradient else androidx.compose.ui.graphics.SolidColor(Line))
                .clickable(valid) {
                    val w = initial.copy(name = name.trim(), query = query.trim(), include = include, exclude = exclude,
                        minPrice = range.start.toInt(), maxPrice = range.endInclusive.toInt(),
                        platforms = ALL_PLATFORMS.filter { it in platforms }, categoryId = category.id, active = true)
                    Store.saveWatch(w)
                    scope.launch(Dispatchers.IO) { SnipeService.checkOne(ctx.applicationContext, w) }
                    if (Store.running.value) SnipeService.start(ctx)
                    onDismiss()
                }, Alignment.Center) {
                Text(if (isNew) "🎯  Start met snipen" else "Opslaan", fontWeight = FontWeight.Bold, color = Color(0xFF1A0D05), fontSize = 16.sp)
            }
            if (!isNew) TextButton({ Store.deleteWatch(initial.id); onDismiss() }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Icon(Icons.Default.Delete, null, tint = Color(0xFFFF7A7A)); Text("  Verwijderen", color = Color(0xFFFF7A7A))
            }
        }
    }
}

private fun Float.roundTo5() = (this / 5).toInt() * 5f

@Composable
private fun Field(label: String, value: String, hint: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text(label) },
        placeholder = { Text(hint, color = Mute.copy(alpha = .6f)) }, singleLine = true, shape = RoundedCornerShape(12.dp))
}

@Composable
private fun PriceBox(value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    OutlinedTextField(value.toString(), { s -> s.filter(Char::isDigit).take(6).toIntOrNull()?.let(onChange) ?: onChange(0) },
        modifier, prefix = { Text("€ ") }, singleLine = true, shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp))
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val running by Store.running.collectAsStateWithLifecycle()
    val interval by Store.intervalMinutes.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Header("Instellingen")
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Op de achtergrond zoeken", fontWeight = FontWeight.Bold)
                    Text("Blijft zoeken als de app dicht is en na herstart", color = Mute, fontSize = 13.sp)
                }
                Switch(running, {
                    Store.setRunning(ctx, it)
                    if (it) SnipeService.start(ctx) else SnipeService.stop(ctx)
                }, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
            }
        }
        Card {
            Text("Elke $interval min zoeken", fontWeight = FontWeight.Bold)
            Text("Sneller = eerder bij een koopje, maar meer batterij", color = Mute, fontSize = 13.sp)
            Slider(interval.toFloat(), { Store.setInterval(ctx, it.toInt()) }, valueRange = 1f..30f, steps = 28,
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Line))
        }
        Card {
            Text("Batterijbesparing uitzetten", fontWeight = FontWeight.Bold)
            Text("Aanbevolen: anders kan Android de app stoppen en mis je meldingen.", color = Mute, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            OutlinedButton({
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
            }, enabled = !pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                Text(if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) "✓ Staat al uit" else "Uitzetten")
            }
        }
        Card {
            Text("Meldingen testen", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({
                    Notifier.deal(ctx, Watch(name = "Test", query = ""), Hit("test", 0, "marktplaats",
                        "Dit is een testmelding van MarktSniper", 199.0, "https://www.marktplaats.nl", null, ""))
                }) { Text("Testmelding") }
                Button({
                    busy = true
                    scope.launch { withContext(Dispatchers.IO) { SnipeService.checkAll(ctx.applicationContext) }; busy = false }
                }, enabled = !busy) { Text(if (busy) "Bezig…" else "Nu alles checken") }
            }
        }
        Text("MarktSniper 1.0 · Vinted kan soms tijdelijk blokkeren; Marktplaats en 2dehands zoeken dan gewoon door.",
            Modifier.padding(20.dp), color = Mute, fontSize = 12.sp)
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(16.dp, 6.dp), RoundedCornerShape(16.dp), color = Panel, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

private fun ago(t: Long) = DateUtils.getRelativeTimeSpanString(t, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

private fun open(ctx: Context, url: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
