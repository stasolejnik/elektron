package pl.zse.bydgoszcz.elektron.presentation.settings


import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import pl.zse.bydgoszcz.elektron.presentation.common.revealWhen
import androidx.compose.foundation.layout.Box
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pl.zse.bydgoszcz.elektron.BuildConfig
import pl.zse.bydgoszcz.elektron.domain.repository.ThemeMode
import pl.zse.bydgoszcz.elektron.presentation.common.GroupedSection
import pl.zse.bydgoszcz.elektron.presentation.common.LargeTitleBar
import pl.zse.bydgoszcz.elektron.presentation.common.pressable
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight


// Lista kategorii Ustawień (karta klasy i grup na górze) i szkielet strony kategorii.

/** Strona kategorii: duży tytuł ze strzałką wstecz i lista sekcji. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSubpage(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTitleBar(title = title, scrollBehavior = scrollBehavior, navigationIcon = {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć do ustawień")
                }
            })
        }
    ) { padding ->
        androidx.compose.runtime.CompositionLocalProvider(pl.zse.bydgoszcz.elektron.presentation.common.LocalSectionPageTitle provides title) {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                content = content
            )
        }
    }
}

/** Lista kategorii: na górze klasa i grupy (od nich zależy wszystko inne), pod nimi kategorie ze stanem. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsHome(
    state: SettingsViewModel.State,
    transit: pl.zse.bydgoszcz.elektron.domain.model.TransitPreferences,
    devMode: Boolean,
    onSelectClass: (String) -> Unit,
    onOpenGroups: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    barState: androidx.compose.material3.TopAppBarState = androidx.compose.material3.rememberTopAppBarState(),
    onOpen: (SettingsPage) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(barState)
    val ctx = LocalContext.current
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LargeTitleBar(title = "Ustawienia", scrollBehavior = scrollBehavior) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).revealWhen(state.ready),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Klasa i grupy osobno, na samej górze - od nich zależy wszystko inne.
            item(key = "class") { ClassCard(state.classes, state.selectedClassId, onSelect = onSelectClass, onOpenGroups = onOpenGroups) }
            item(key = "main") {
                GroupedSection(null) {
                    val notifications = listOfNotNull(
                        "zastępstwa".takeIf { state.notifSubs }, "ogłoszenia".takeIf { state.notifAnn },
                        "informacje szkoły".takeIf { state.notifyNotices },
                        "nowa wersja".takeIf { state.notifyUpdates && BuildConfig.UPDATE_CHECK },
                        "na żywo".takeIf { state.liveNotifications },
                        "przypomnienia".takeIf { state.reminder.mode != pl.zse.bydgoszcz.elektron.domain.model.ReminderMode.OFF })
                    CategoryRow(Icons.Outlined.Notifications, "Powiadomienia",
                        notifications.joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { "Wyłączone" }) { onOpen(SettingsPage.NOTIFICATIONS) }
                    CategoryDivider()
                    CategoryRow(Icons.Outlined.Home, "Strona główna",
                        "Start: " + startScreenLabel(state.startScreen, transit.visible).lowercase()) { onOpen(SettingsPage.HOME) }
                    CategoryDivider()
                    val wallpaper = state.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    val theme = when (state.themeMode) { ThemeMode.SYSTEM -> "Auto"; ThemeMode.LIGHT -> "Jasny"; ThemeMode.DARK -> "Ciemny" }
                    CategoryRow(Icons.Outlined.Palette, "Wygląd",
                        if (wallpaper) "$theme · kolory z tapety"
                        else "$theme · ${ACCENT_LABELS[state.accent.color].orEmpty().lowercase()} · ${LOOK_LABELS[state.baseLook.look].orEmpty().lowercase()}") { onOpen(SettingsPage.LOOK) }
                    CategoryDivider()
                    CategoryRow(Icons.Outlined.CalendarMonth, "Plan lekcji", "Nazwy i kolory przedmiotów, sala, nauczyciel") { onOpen(SettingsPage.PLAN) }
                    CategoryDivider()
                    CategoryRow(Icons.Outlined.DirectionsBus, "Przejazdy",
                        if (transit.visible || transit.showOnDashboard) transit.destination?.name ?: "Wybierz swój przystanek" else "Wyłączone") { onOpen(SettingsPage.TRANSIT) }
                    CategoryDivider()
                    CategoryRow(Icons.Outlined.SpaceDashboard, "Pasek nawigacji", "Widoczne zakładki i kolejność") { onOpen(SettingsPage.BAR) }
                    CategoryDivider()
                    CategoryRow(Icons.Outlined.Widgets, "Widżety",
                        "Przezroczystość ${100 - state.widgetLook.opacity}%") { onOpen(SettingsPage.WIDGETS) }
                }
            }
            item(key = "system") {
                GroupedSection(null) {
                    var unrestricted by remember { mutableStateOf(pl.zse.bydgoszcz.elektron.presentation.common.BackgroundWork.isUnrestricted(ctx)) }
                    OnResume { unrestricted = pl.zse.bydgoszcz.elektron.presentation.common.BackgroundWork.isUnrestricted(ctx) }
                    CategoryRow(Icons.Outlined.BatteryChargingFull, "Praca w tle",
                        if (unrestricted) "Bez ograniczeń" else "Telefon może usypiać aplikację", warning = !unrestricted) { onOpen(SettingsPage.BACKGROUND) }
                    if (BuildConfig.UPDATE_CHECK) {
                        CategoryDivider()
                        CategoryRow(Icons.Outlined.SystemUpdate, "Aktualizacje",
                            state.updateChannel.label) { onOpen(SettingsPage.UPDATES) }
                    }
                    CategoryDivider()
                    CategoryRow(Icons.Outlined.Storage, "Dane", "Wyczyść dane podręczne") { onOpen(SettingsPage.DATA) }
                    CategoryDivider()
                    CategoryRow(Icons.Outlined.Info, "O aplikacji", "Wersja ${BuildConfig.VERSION_NAME} · zgłoś problem") { onOpen(SettingsPage.ABOUT) }
                    if (devMode) {
                        CategoryDivider()
                        CategoryRow(Icons.Outlined.Code, "Tryb dewelopera", "Symulacje i czas") { onOpen(SettingsPage.DEV) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CategoryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, summary: String,
    warning: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().pressable(onClickLabel = "Otwórz: $title", onClick = onClick)
            .heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Jeden spokojny kolor (tło wyróżnienia w kolorze akcentu) zamiast osobnej barwy dla każdej kategorii.
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(colors.secondaryContainer),
            contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                fontWeight = if (warning) FontWeight.SemiBold else null,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

/** Linia między kategoriami - od tekstu, nie od kafelka ikony (jak w iOS). */
@Composable
internal fun CategoryDivider() {
    HorizontalDivider(Modifier.padding(start = 62.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
