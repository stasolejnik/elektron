package pl.zse.bydgoszcz.elektron

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import pl.zse.bydgoszcz.elektron.domain.repository.ThemeMode
import pl.zse.bydgoszcz.elektron.presentation.common.ChangelogDialog
import pl.zse.bydgoszcz.elektron.presentation.common.ElektronAppViewModel
import pl.zse.bydgoszcz.elektron.presentation.common.theme.ElektronTheme
import pl.zse.bydgoszcz.elektron.presentation.groups.GroupsScreen
import pl.zse.bydgoszcz.elektron.presentation.navigation.ElektronNavHost
import pl.zse.bydgoszcz.elektron.presentation.setup.SetupScreen
import pl.zse.bydgoszcz.elektron.work.LocalNotificationSink

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: ElektronAppViewModel by viewModels()
    private val pendingDeepLink = MutableStateFlow<String?>(null)

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Tylko przy pierwszym utworzeniu Activity. Przy odtworzeniu (np. obrót ekranu)
        // intent jest ten sam — dawniej deep link wykonywał się ponownie (dla linku http:
        // ponowne otwarcie przeglądarki), a prośba o uprawnienie pojawiała się od nowa.
        if (savedInstanceState == null) {
            pendingDeepLink.value = resolveDeepLink(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val state by appViewModel.state.collectAsStateWithLifecycle()
            val deepLink by pendingDeepLink.collectAsStateWithLifecycle()
            val dark = when (state.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(Unit) { appViewModel.ensureLastSeenInitialized() }

            ElektronTheme(darkTheme = dark) {
                when {
                    !state.isReady -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.selectedClassId == null -> SetupScreen()
                    // Po wyborze klasy (w Setup albo w Ustawieniach) — krok wyboru grup.
                    state.groupsConfiguredFor != state.selectedClassId -> GroupsScreen(asSetupStep = true)
                    else -> ElektronNavHost(
                        deepLink = deepLink,
                        onDeepLinkConsumed = { pendingDeepLink.value = null }
                    )
                }

                if (state.changelogToShow.isNotEmpty()) {
                    ChangelogDialog(
                        entries = state.changelogToShow,
                        onDismiss = { appViewModel.markChangelogSeen() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink.value = resolveDeepLink(intent)
    }

    private fun resolveDeepLink(intent: Intent?): String? {
        intent ?: return null
        val fromNotif = intent.getStringExtra(LocalNotificationSink.EXTRA_DEEP_LINK)
        if (!fromNotif.isNullOrBlank()) {
            if (fromNotif.startsWith("http://") || fromNotif.startsWith("https://")) {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fromNotif)))
                }
                return null
            }
            return fromNotif
        }
        return when (intent.getStringExtra("elektron_shortcut")) {
            "substitutions" -> "substitutions"
            "announcements" -> "announcements"
            "timetable" -> "timetable" // widżety
            else -> null
        }
    }
}
