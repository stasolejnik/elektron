package pl.zse.bydgoszcz.elektron.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.zse.bydgoszcz.elektron.BuildConfig
import pl.zse.bydgoszcz.elektron.R
import pl.zse.bydgoszcz.elektron.domain.repository.ThemeMode
import pl.zse.bydgoszcz.elektron.presentation.common.GroupedSection
import pl.zse.bydgoszcz.elektron.presentation.common.LargeTitleBar
import pl.zse.bydgoszcz.elektron.presentation.common.isolatedVerticalScroll
import pl.zse.bydgoszcz.elektron.presentation.common.pressable

private const val REPO_URL = "https://github.com/stasolejnik/elektron"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenGroups: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val linkColor = MaterialTheme.colorScheme.primary
    val ctx = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LargeTitleBar(title = "Ustawienia", scrollBehavior = scrollBehavior) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                GroupedSection("Wygląd") {
                    SingleChoiceSegmentedButtonRow(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        val options = listOf(ThemeMode.SYSTEM to "Auto", ThemeMode.LIGHT to "Jasny", ThemeMode.DARK to "Ciemny")
                        options.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = state.themeMode == mode,
                                onClick = { viewModel.setTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                                icon = {}
                            ) { Text(label) }
                        }
                    }
                }
            }

            item {
                GroupedSection("Klasa", footer = "Plan, zastępstwa i powiadomienia dotyczą wybranej klasy.") {
                    if (state.classes.isEmpty()) {
                        Text("Brak listy klas. Pociągnij w dół na Starcie, aby odświeżyć.",
                            Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        // Przewijana lista o ograniczonej wysokości, od razu przy wybranej klasie.
                        val listState = rememberLazyListState()
                        val selectedIndex = state.classes.indexOfFirst { it.id == state.selectedClassId }
                        LaunchedEffect(selectedIndex) {
                            if (selectedIndex > 0) listState.scrollToItem((selectedIndex - 1).coerceAtLeast(0))
                        }
                        // Przewija się TYLKO lista klas — ekran Ustawień i duży tytuł stoją
                        // w miejscu, także po dojechaniu do początku/końca listy.
                        LazyColumn(
                            state = listState,
                            userScrollEnabled = false,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 264.dp)
                                .isolatedVerticalScroll(listState)
                        ) {
                            items(state.classes, key = { it.id }) { c ->
                                val selected = state.selectedClassId == c.id
                                Row(
                                    Modifier.fillMaxWidth()
                                        .pressable { viewModel.setClass(c.id) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(c.fullName, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    if (selected) {
                                        Icon(Icons.Filled.Check, contentDescription = "Wybrana",
                                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                }
                                HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }

            item {
                GroupedSection("Grupy zajęciowe", footer = "Wybierz swoje grupy (np. językowe, zajęcia praktyczne) — w planie i na Starcie zobaczysz tylko swoje lekcje.") {
                    ActionRow("Wybierz swoje grupy", trailingChevron = true, onClick = onOpenGroups)
                }
            }

            item {
                GroupedSection("Ekran Start") {
                    SwitchRow("Następna lekcja", state.showNextLesson) { viewModel.setShowNextLesson(it) }
                    RowDivider()
                    SwitchRow("Zastępstwa", state.showSubstitutions) { viewModel.setShowSubstitutions(it) }
                    RowDivider()
                    SwitchRow("Ogłoszenia", state.showAnnouncements) { viewModel.setShowAnnouncements(it) }
                }
            }

            item {
                GroupedSection("Powiadomienia") {
                    SwitchRow("Nowe zastępstwa", state.notifSubs) { viewModel.setNotifSubs(it) }
                    RowDivider()
                    SwitchRow("Nowe ogłoszenia", state.notifAnn) { viewModel.setNotifAnn(it) }
                }
            }

            item {
                GroupedSection("O aplikacji") {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = null,
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("eLektron", style = MaterialTheme.typography.titleLarge)
                        Text("Wersja ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(aboutText(linkColor),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                    RowDivider()
                    ActionRow("Kod źródłowy na GitHubie", trailingIcon = true) {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))) }
                    }
                    RowDivider()
                    ActionRow("Licencja: GNU GPL v3.0 lub nowsza", trailingIcon = true) {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html")))
                        }
                    }
                }
            }

            item {
                Text(
                    "Źródła danych: plan.zse.bydgoszcz.pl, zastepstwa.zse.bydgoszcz.pl, zse.bydgoszcz.pl (RSS). " +
                        "Dane pobierane są wyłącznie z publicznie dostępnych stron szkoły, z poszanowaniem robots.txt.",
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun aboutText(linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        append("Nieoficjalny odpowiednik aplikacji ")
        link("Atom", "https://github.com/kacpergorka/atom", linkColor)
        append(" autorstwa ")
        link("Kacpra Górki", "https://github.com/kacpergorka", linkColor)
        append(", przeznaczony na Androida. Projekt niezależny, niepowiązany z ZSE w Bydgoszczy ani z firmą VULCAN.")
    }

private fun AnnotatedString.Builder.link(text: String, url: String, color: Color) {
    val start = length
    append(text)
    addStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline), start, length)
    addLink(LinkAnnotation.Url(url), start, length)
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = Color.White,
                checkedBorderColor = Color.Transparent,
                uncheckedBorderColor = Color.Transparent,
                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                uncheckedThumbColor = Color.White
            ),
            thumbContent = null
        )
    }
}

@Composable
private fun ActionRow(
    label: String,
    destructive: Boolean = false,
    trailingIcon: Boolean = false,
    trailingChevron: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        if (trailingIcon) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        if (trailingChevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
    }
}
