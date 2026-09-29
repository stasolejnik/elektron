package pl.zse.bydgoszcz.elektron.crash

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement

/** Okno po awarii: zgłoszenie na GitHubie, udostępnienie raportu albo pominięcie. */
@Composable
fun CrashReportDialog(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aplikacja nieoczekiwanie się zamknęła") },
        text = {
            Column {
                Text("Zgłoszenie pomoże naprawić ten błąd.")
                Text(
                    "Raport zawiera tylko dane techniczne: wersję aplikacji, model telefonu " +
                        "i opis błędu. Nic nie zostanie wysłane bez Twojej zgody.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { share(context, report); onDismiss() }) { Text("Udostępnij") }
                TextButton(onClick = { openIssue(context, report); onDismiss() }) { Text("Zgłoś na GitHubie") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Pomiń") } }
    )
}

private fun openIssue(context: Context, report: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(CrashReport.issueUrl(report)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {
        share(context, report)   // brak przeglądarki - zostaje udostępnienie
    }
}

private fun share(context: Context, report: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, CrashReport.title(report))
        .putExtra(Intent.EXTRA_TEXT, report)
    try {
        context.startActivity(Intent.createChooser(send, "Udostępnij raport").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) { }
}
