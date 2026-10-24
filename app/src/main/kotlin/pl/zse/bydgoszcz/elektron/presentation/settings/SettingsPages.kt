package pl.zse.bydgoszcz.elektron.presentation.settings


import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import android.os.SystemClock
import androidx.compose.foundation.layout.width
import pl.zse.bydgoszcz.elektron.crash.openContactEmail
import androidx.compose.foundation.layout.Box
import pl.zse.bydgoszcz.elektron.presentation.common.UpdateActions
import pl.zse.bydgoszcz.elektron.crash.CrashReport
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.zse.bydgoszcz.elektron.BuildConfig
import pl.zse.bydgoszcz.elektron.R
import pl.zse.bydgoszcz.elektron.presentation.common.GroupedSection
import pl.zse.bydgoszcz.elektron.presentation.common.SafeUrls
import pl.zse.bydgoszcz.elektron.presentation.common.pressable
import pl.zse.bydgoszcz.elektron.domain.model.UpdateChannel
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate


// Sekcje stron kategorii: aktualizacje, dane, o aplikacji, tryb dewelopera.

@Composable
internal fun UpdatesSection(state: SettingsViewModel.State, viewModel: SettingsViewModel) {
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    var choosingChannel by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> denied = !granted; if (granted) viewModel.setNotifyUpdates(true) }
    GroupedSection("Aktualizacje", icon = Icons.Outlined.SystemUpdate,
        footer = if (state.updateChannel == UpdateChannel.BETA)
            "Wersje beta są testowe i mogą zawierać błędy. Gdy ukaże się wersja stabilna, też ją dostaniesz."
        else null) {
        ValueRow("Kanał", state.updateChannel.label) { choosingChannel = true }
        RowDivider()
        SwitchRow("Powiadamiaj o nowej wersji", state.notifyUpdates,
            supporting = "Sprawdzanie w tle najwyżej co 12 godzin, jedno powiadomienie na wersję") { enable ->
            val needsPermission = enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (needsPermission) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) else viewModel.setNotifyUpdates(enable)
        }
        if (denied) Text("Bez zgody na powiadomienia o nowej wersji dowiesz się z baneru na stronie głównej.",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RowDivider()
        ActionRow("Sprawdź aktualizacje", enabled = updateStatus != SettingsViewModel.UpdateStatus.Checking) { viewModel.checkForUpdates() }
        when (val st = updateStatus) {
            SettingsViewModel.UpdateStatus.Idle -> Unit
            SettingsViewModel.UpdateStatus.Checking -> UpdateNote("Sprawdzam…")
            SettingsViewModel.UpdateStatus.UpToDate -> UpdateNote("Masz najnowszą wersję (${BuildConfig.VERSION_NAME}).")
            SettingsViewModel.UpdateStatus.Failed -> UpdateNote("Nie udało się sprawdzić - brak połączenia z GitHubem.")
            is SettingsViewModel.UpdateStatus.Available -> {
                UpdateNote("Dostępna wersja ${st.update.versionName}.")
                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    UpdateActions(st.update, onLater = null)
                }
            }
        }
    }
    if (choosingChannel) {
        ChoiceDialog("Kanał aktualizacji",
            listOf(UpdateChannel.STABLE to "Stabilne (zalecane)", UpdateChannel.BETA to "Beta - wersje testowe i stabilne"),
            state.updateChannel,
            onSelect = { viewModel.setUpdateChannel(it); choosingChannel = false },
            onDismiss = { choosingChannel = false })
    }
}

@Composable
internal fun DataSection(viewModel: SettingsViewModel) {
    var confirmReset by remember { mutableStateOf(false) }
    GroupedSection("Dane", icon = Icons.Outlined.Storage,
        footer = "Pobiera plan, zastępstwa i ogłoszenia od nowa. Klasa, grupy, notatki i ustawienia zostają.") {
        ActionRow("Wyczyść dane podręczne", destructive = true) { confirmReset = true }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Wyczyścić dane podręczne?") },
            text = { Text("Lokalna kopia planu, zastępstw i ogłoszeń zostanie usunięta i pobrana od nowa ze strony szkoły.") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetCache(); confirmReset = false }) {
                    Text("Wyczyść", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Anuluj") } }
        )
    }
}

@Composable
internal fun AboutSection(viewModel: SettingsViewModel, devMode: Boolean, showToast: (String) -> Unit) {
    val ctx = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    var logoTaps by remember { mutableIntStateOf(0) }
    var lastLogoTap by remember { mutableLongStateOf(0L) }
    val onLogoTap: () -> Unit = {
        val t = SystemClock.uptimeMillis()
        logoTaps = if (t - lastLogoTap < 700) logoTaps + 1 else 1
        lastLogoTap = t
        if (!devMode && logoTaps >= 5) {
            viewModel.setDevMode(true)
            logoTaps = 0
            showToast("Tryb dewelopera włączony.")
        }
    }
    GroupedSection("O aplikacji", icon = Icons.Outlined.Info) {
        Column(
            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onLogoTap
                    )
            )
            Spacer(Modifier.height(10.dp))
            Text("eLektron", style = MaterialTheme.typography.titleLarge)
            Text("Wersja ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(aboutText(linkColor),
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        RowDivider()
        // Raport z logami: kopiowanie, e-mail do dewelopera albo zgłoszenie na GitHubie.
        ActionRow("Zgłoś problem", trailingChevron = true) { viewModel.openFeedback() }
        RowDivider()
        ActionRow("Napisz do autora", supporting = CrashReport.CONTACT_EMAIL, trailingIcon = true) {
            if (!openContactEmail(ctx)) SafeUrls.open(ctx, REPO_URL)
        }
        RowDivider()
        ActionRow("Kod źródłowy na GitHubie", trailingIcon = true) { SafeUrls.open(ctx, REPO_URL) }
        RowDivider()
        ActionRow("Polityka prywatności", trailingIcon = true) { SafeUrls.open(ctx, PRIVACY_URL) }
        RowDivider()
        // Licencja: plakietka GPLv3 (dotknięcie otwiera tekst licencji).
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.gpl_v3),
                contentDescription = "Licencja GNU GPL w wersji 3 lub nowszej - otwórz tekst licencji",
                modifier = Modifier.width(112.dp).pressable { SafeUrls.open(ctx, LICENSE_URL) }
            )
        }
    }
}

@Composable
internal fun DevSection(viewModel: SettingsViewModel) {
    var confirmCrash by remember { mutableStateOf(false) }
    if (confirmCrash) {
        AlertDialog(
            onDismissRequest = { confirmCrash = false },
            title = { Text("Symulować awarię?") },
            text = { Text("Aplikacja zamknie się z błędem. Po ponownym uruchomieniu pojawi się okno raportu (kopiowanie logów, e-mail, GitHub).") },
            confirmButton = { TextButton(onClick = { confirmCrash = false; viewModel.devCrash() }) { Text("Zamknij z błędem") } },
            dismissButton = { TextButton(onClick = { confirmCrash = false }) { Text("Anuluj") } }
        )
    }
    val devDialog by viewModel.devDialog.collectAsStateWithLifecycle()
    DevSimulationDialogs(devDialog,
        onSubstitution = viewModel::devSimulateSubstitution,
        onAnnouncement = viewModel::devSimulateAnnouncement,
        onCustomSubstitution = { viewModel.devDialog.value = SettingsViewModel.DevDialog.CustomSubstitution },
        onCustomAnnouncement = { viewModel.devDialog.value = SettingsViewModel.DevDialog.CustomAnnouncement },
        onDismiss = { viewModel.devDialog.value = null })
    val offset by viewModel.simulatedOffset.collectAsStateWithLifecycle()
    GroupedSection(
        "Tryb dewelopera",
        icon = Icons.Outlined.Code,
        footer = "Symulacje są widoczne tylko na tym telefonie. Zastępstwo (własne albo wzorowane na zapisanym, " +
            "także minionym) trafia na najbliższą lekcję - do planu, zastępstw, strony głównej i widżetów - " +
            "i wysyła powiadomienie; tak samo ogłoszenie. Znikają po „Usuń symulacje” albo przy synchronizacji. " +
            "Symulowany czas dalej płynie; synchronizacja, przypomnienia i powiadomienia zostają w prawdziwym czasie."
    ) {
        DevTimeRows(offset, onSimulate = viewModel::simulateTime, onReset = viewModel::resetSimulatedTime)
        RowDivider()
        ActionRow("Symuluj zastępstwo…", trailingChevron = true) { viewModel.devOpenSubstitution() }
        RowDivider()
        ActionRow("Symuluj ogłoszenie…", trailingChevron = true) { viewModel.devOpenAnnouncement() }
        RowDivider()
        ActionRow("Usuń symulacje") { viewModel.devClearSimulations() }
        RowDivider()
        ActionRow("Odśwież widżety") { viewModel.devRefreshWidgets() }
        RowDivider()
        ActionRow("Raport błędu (bez awarii)") { viewModel.openFeedback() }
        RowDivider()
        ActionRow("Symuluj awarię aplikacji", destructive = true) { confirmCrash = true }
        RowDivider()
        ActionRow("Wyłącz tryb dewelopera") { viewModel.setDevMode(false) }
    }
}

internal fun aboutText(linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        append("Nieoficjalny odpowiednik aplikacji ")
        link("Atom", "https://github.com/kacpergorka/atom", linkColor)
        append(" autorstwa ")
        link("Kacpra Górki", "https://github.com/kacpergorka", linkColor)
        append(", przeznaczony na Androida. Projekt niezależny, niepowiązany z ZSE w Bydgoszczy ani z firmą VULCAN. ")
        append("Kod aplikacji napisało bezduszne AI, głównie Claude (Anthropic) i ChatGPT (OpenAI).")
    }

internal fun AnnotatedString.Builder.link(text: String, url: String, color: Color) {
    val start = length
    append(text)
    addStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline), start, length)
    addLink(LinkAnnotation.Url(url), start, length)
}

@Composable
internal fun UpdateNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
}
