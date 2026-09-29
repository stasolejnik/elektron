package pl.zse.bydgoszcz.elektron.presentation.groups

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import pl.zse.bydgoszcz.elektron.presentation.common.ElektronCard

/**
 * Wybór grup zajęciowych.
 * [asSetupStep] = true: krok po wyborze klasy (bez strzałki wstecz, przycisk "Dalej").
 * [asSetupStep] = false: podstrona Ustawień (strzałka wstecz, zmiany zapisują się od razu).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    asSetupStep: Boolean,
    onClose: () -> Unit = {},
    viewModel: GroupsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text("Twoje grupy") },
                navigationIcon = {
                    if (!asSetupStep) {
                        IconButton(onClick = { viewModel.finish(onClose) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            if (asSetupStep) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Button(
                        onClick = { viewModel.finish(onClose) },
                        enabled = state.status != GroupsViewModel.Status.LOADING,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp).height(52.dp)
                    ) {
                        Text(if (state.status == GroupsViewModel.Status.FAILED) "Pomiń na razie" else "Dalej",
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = state.status,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "groupsStatus",
            modifier = Modifier.fillMaxSize().padding(padding)
        ) { status ->
            when (status) {
                GroupsViewModel.Status.LOADING -> CenterMessage(loading = true,
                    title = "Pobieram plan lekcji…",
                    body = "Sprawdzam, które zajęcia mają podział na grupy.")
                GroupsViewModel.Status.NO_GROUPS -> CenterMessage(
                    title = "Brak podziału na grupy",
                    body = "W planie Twojej klasy nie ma zajęć z podziałem. Wszystkie lekcje będą widoczne.")
                GroupsViewModel.Status.FAILED -> CenterMessage(
                    title = "Nie udało się pobrać planu",
                    body = "Grupy możesz ustawić później w Ustawieniach.",
                    actionLabel = if (state.retrying) null else "Spróbuj ponownie",
                    onAction = viewModel::retry)
                GroupsViewModel.Status.READY -> SubjectList(state, viewModel::setChoice, viewModel::applyDivision)
            }
        }
    }
}

@Composable
private fun SubjectList(
    state: GroupsViewModel.State,
    onChoice: (String, String?) -> Unit,
    onDivision: (String, String?) -> Unit
) {
    val divisions = remember(state.subjects) { LessonGroups.divisions(state.subjects) }
    val customizable = remember(state.subjects) { LessonGroups.orderForCustomizing(state.subjects) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "intro") {
            Text(
                "Wybierz grupy, do których należysz. W planie i na stronie głównej zobaczysz tylko swoje " +
                    "zajęcia — lekcje bez podziału są widoczne zawsze." +
                    (state.className?.let { "\nKlasa: $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        if (divisions.isNotEmpty()) {
            item(key = "quick_title") { SectionLabel("Szybki wybór") }
            items(divisions, key = { "div_${it.key}" }) { division ->
                DivisionCard(
                    division = division,
                    selected = LessonGroups.selectedDivisionOption(division, state.subjects, state.selections),
                    onSelect = { option -> onDivision(division.key, option) }
                )
            }
            item(key = "each_title") { SectionLabel("Dostosuj osobno") }
        }
        items(customizable, key = { it.base }) { subject ->
            SubjectCard(subject, state.selections[subject.base]) { onChoice(subject.base, it) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectCard(
    subject: LessonGroups.DividedSubject,
    selection: String?,
    onChoice: (String?) -> Unit
) {
    ElektronCard {
        Column(Modifier.padding(16.dp)) {
            if (subject.isToggle) {
                // Jedna grupa (np. religia 1/1) — tylko "chodzę / nie chodzę".
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(subject.base, style = MaterialTheme.typography.titleMedium)
                        Text("Pokazuj te zajęcia w planie", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = selection != LessonGroups.NONE,
                        onCheckedChange = { on -> onChoice(if (on) null else LessonGroups.NONE) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = Color.White,
                            checkedBorderColor = Color.Transparent,
                            uncheckedBorderColor = Color.Transparent,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                            uncheckedThumbColor = Color.White
                        )
                    )
                }
            } else {
                Text(subject.base, style = MaterialTheme.typography.titleMedium)
                Text(
                    when (selection) {
                        null -> "Widoczne wszystkie grupy"
                        LessonGroups.NONE -> "Nie chodzisz na te zajęcia"
                        else -> "Twoja grupa: ${LessonGroups.displayLabel(selection)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // "Wszystkie" = chodzisz na wszystkie grupy (np. 1/2 i 2/2) — wszystkie widoczne.
                    ChoiceChip("Wszystkie", selection == null) { onChoice(null) }
                    subject.labels.forEach { label ->
                        ChoiceChip(LessonGroups.displayLabel(label), selection == label) {
                            onChoice(if (selection == label) null else label)
                        }
                    }
                    ChoiceChip("Nie chodzę", selection == LessonGroups.NONE) {
                        onChoice(if (selection == LessonGroups.NONE) null else LessonGroups.NONE)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
private fun CenterMessage(
    title: String,
    body: String,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) CircularProgressIndicator()
            else Icon(Icons.Outlined.Groups, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp))
}

/** Jeden typ podziału (np. "Podział na 2 grupy": Wszystkie · 1/2 · 2/2) dla wszystkich jego przedmiotów. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DivisionCard(division: LessonGroups.Division, selected: String?, onSelect: (String?) -> Unit) {
    ElektronCard {
        Column(Modifier.padding(16.dp)) {
            Text(division.title, style = MaterialTheme.typography.titleMedium)
            Text(division.subjects.joinToString(", "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Wszystkie", selected == "") { onSelect(null) }
                division.options.forEach { option ->
                    // Ułamki jak w planie szkoły ("1/2"), grupy literowe numerem ("j1" -> "1").
                    val label = if ('/' in option) option else (LessonGroups.labelNumber(option)?.toString() ?: option)
                    ChoiceChip(label, selected == option) { onSelect(option) }
                }
            }
        }
    }
}
