package pl.zse.bydgoszcz.elektron.presentation.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.nayuki.qrcodegen.QrCode
import pl.zse.bydgoszcz.elektron.R

/** Adres w kodzie QR i w udostępnianym tekście: repozytorium eLektronu na GitHubie. */
internal const val RECOMMEND_URL = REPO_URL

internal const val RECOMMEND_TEXT =
    "eLektron - plan lekcji, zastępstwa, ogłoszenia i przejazdy dla uczniów ZSE w Bydgoszczy. " +
        "Repozytorium na GitHubie: $RECOMMEND_URL"

/** Granat z ikony aplikacji: moduły kodu (kontrast z białym jak czarny). */
private val QR_DARK = Color(0xFF0B2545)

/**
 * Moduły kodu QR dla [text]. Poziom korekcji HIGH: pusty środek pod logo to ok. 6% modułów,
 * a kod znosi utratę 30%.
 */
internal fun recommendQr(text: String = RECOMMEND_URL): QrCode = QrCode.encodeText(text, QrCode.Ecc.HIGH)

/** Moduły (w pionie i poziomie) pustego środka pod logo: ok. 25% szerokości, nieparzyście, na środku. */
internal fun logoHole(size: Int): IntRange {
    val side = (size * 0.25f).toInt().let { if (it % 2 == 0) it + 1 else it }
    val start = (size - side) / 2
    return start until start + side
}

/** "Poleć aplikację": kod QR z logo do zeskanowania z ekranu i udostępnienie linku. */
@Composable
internal fun RecommendDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Poleć eLektron") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                QrWithLogo(Modifier.size(232.dp))
                Text(
                    "Zeskanuj kod aparatem telefonu, aby otworzyć repozytorium eLektronu na GitHubie.",
                    Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { shareApp(ctx) }) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                Text("Udostępnij")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } }
    )
}

@Composable
private fun QrWithLogo(modifier: Modifier) {
    val qr = remember { recommendQr() }
    // Białe tło także w ciemnym motywie - aparaty czytają ciemne moduły na jasnym.
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)
            .semantics { contentDescription = "Kod QR z linkiem do pobrania eLektronu" },
        contentAlignment = Alignment.Center
    ) {
        val hole = logoHole(qr.size)
        Canvas(Modifier.fillMaxSize()) {
            val n = qr.size
            val cell = size.minDimension / n
            for (y in 0 until n) for (x in 0 until n) {
                if (x in hole && y in hole) continue
                // Nakładka o pół piksela - bez jasnych szczelin między modułami.
                if (qr.getModule(x, y)) drawRect(QR_DARK, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
        // Samo logo (przezroczyste tło) w pustym środku kodu.
        Image(painterResource(R.drawable.ic_logo), contentDescription = null,
            modifier = Modifier.fillMaxSize((hole.last - hole.first + 1).toFloat() / qr.size))
    }
}

private fun shareApp(ctx: Context) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "eLektron")
        putExtra(Intent.EXTRA_TEXT, RECOMMEND_TEXT)
    }
    runCatching { ctx.startActivity(Intent.createChooser(send, "Poleć eLektron")) }
}
