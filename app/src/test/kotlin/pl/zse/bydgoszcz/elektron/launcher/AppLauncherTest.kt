package pl.zse.bydgoszcz.elektron.launcher

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLauncherTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val shortcuts get() = context.getSystemService(ShortcutManager::class.java)!!.dynamicShortcuts

    private fun enabledAliases() = AppIcon.entries.filter {
        context.packageManager.getComponentEnabledSetting(AppIcons.component(context, it)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    @Test fun defaultIconUntilChangedAndExactlyOneLauncherEntryAfterChange() {
        assertEquals(AppIcon.DEFAULT, AppIcons.current(context))
        AppIcons.set(context, AppIcon.LIGHT)
        assertEquals(AppIcon.LIGHT, AppIcons.current(context))
        assertEquals(listOf(AppIcon.LIGHT), enabledAliases())
        AppIcons.set(context, AppIcon.DEFAULT)
        assertEquals(AppIcon.DEFAULT, AppIcons.current(context))
        assertEquals(listOf(AppIcon.DEFAULT), enabledAliases())
    }

    @Test fun shortShortcutNamesAndTransitOnlyWithItsTab() {
        AppShortcuts.update(context, transitTabVisible = false)
        assertEquals(listOf("Plan lekcji", "Zastępstwa", "Ogłoszenia", "Strona główna"),
            shortcuts.sortedBy { it.rank }.map { it.shortLabel.toString() })
        AppShortcuts.update(context, transitTabVisible = true)
        assertEquals(listOf("Plan lekcji", "Zastępstwa", "Odjazdy", "Ogłoszenia", "Strona główna"),
            shortcuts.sortedBy { it.rank }.map { it.shortLabel.toString() })
        assertEquals("transit", shortcuts.single { it.id == "odjazdy" }.intent?.getStringExtra(AppShortcuts.EXTRA_SHORTCUT))
    }

    @Test fun shortcutsFollowTheChosenIcon() {
        AppShortcuts.update(context, transitTabVisible = false)
        AppIcons.set(context, AppIcon.LIGHT)
        assertEquals(setOf(AppIcons.component(context, AppIcon.LIGHT)), shortcuts.map { it.activity }.toSet())
        AppIcons.set(context, AppIcon.DEFAULT)
    }

    @Test fun retiredClearIconSwitchesBackToDefault() {
        val retired = android.content.ComponentName(context.packageName, "pl.zse.bydgoszcz.elektron.IconClear")
        val pm = context.packageManager
        pm.setComponentEnabledSetting(retired, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(AppIcons.component(context, AppIcon.DEFAULT), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        AppIcons.migrate(context)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(retired))
        assertEquals(listOf(AppIcon.DEFAULT), enabledAliases())
    }
}
