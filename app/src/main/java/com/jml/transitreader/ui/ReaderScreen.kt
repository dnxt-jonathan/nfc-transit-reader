package com.jml.transitreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jml.transitreader.parser.CardInfo
import com.jml.transitreader.parser.ClipperCard
import com.jml.transitreader.parser.ClipperTrip
import com.jml.transitreader.parser.ReadResult
import com.jml.transitreader.parser.SuicaCard
import com.jml.transitreader.parser.SuicaTransaction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a")

private fun formatCents(cents: Int): String {
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents)
    return "%s$%d.%02d".format(sign, abs / 100, abs % 100)
}

private fun formatTime(unix: Long?): String? {
    if (unix == null || unix <= 0) return null
    return try {
        Instant.ofEpochSecond(unix).atZone(ZoneId.systemDefault()).format(DATE_FMT)
    } catch (_: Exception) {
        null
    }
}

private fun formatYen(yen: Int): String = "¥%,d".format(yen)

private fun formatYenSigned(yen: Int): String =
    if (yen >= 0) "+¥%,d".format(yen) else "-¥%,d".format(-yen)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(result: ReadResult?, nfcEnabled: Boolean) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("NFC Transit Reader") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (!nfcEnabled) {
                InfoCard(
                    title = "NFC is off",
                    body = "Turn on NFC in your phone's Quick Settings or system settings, then tap a card."
                )
                return@Column
            }
            when (result) {
                null -> PromptState()
                is ReadResult.Clipper -> ClipperView(result.card)
                is ReadResult.Suica -> SuicaView(result.card)
                is ReadResult.Unsupported -> UnsupportedView(result.info)
                is ReadResult.Failure -> InfoCard(title = "Couldn't read that card", body = result.message)
            }
        }
    }
}

@Composable
private fun PromptState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📶", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            "Tap a transit card to the back of your phone",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Hold it still against the NFC antenna until the balance appears.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ClipperView(card: ClipperCard) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Clipper", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("Balance", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        formatCents(card.balanceCents),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (card.serial >= 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Serial  ${card.serial}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    formatTime(card.lastTransactionTimeUnix)?.let {
                        Text("Last transaction  $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Text(
                if (card.trips.isEmpty()) "No trips recorded on this card"
                else "Recent trips (${card.trips.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(card.trips) { trip -> TripRow(trip) }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TripRow(trip: ClipperTrip) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(trip.agencyName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (trip.fareCents == 0) "—" else formatCents(trip.fareCents),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            formatTime(trip.boardingTimeUnix)?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            val route = listOfNotNull(trip.fromStation, trip.toStation)
            if (route.isNotEmpty()) {
                Text(route.joinToString("  →  "), style = MaterialTheme.typography.bodySmall)
            }
            trip.vehicleId?.let {
                Text("Vehicle $it", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SuicaView(card: SuicaCard) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Suica / ICOCA / PASMO", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("Balance", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        formatYen(card.balanceYen),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "IDm  ${card.idm}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        item {
            Text(
                if (card.transactions.isEmpty()) "No transactions recorded"
                else "History (${card.transactions.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(card.transactions) { txn -> SuicaTxnRow(txn) }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SuicaTxnRow(txn: SuicaTransaction) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(txn.type, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                txn.amountYen?.let {
                    Text(formatYenSigned(it), style = MaterialTheme.typography.titleSmall)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "%04d-%02d-%02d".format(txn.year, txn.month, txn.day),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Balance ${formatYen(txn.balanceYen)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UnsupportedView(info: CardInfo) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Card detected", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("UID", style = MaterialTheme.typography.labelMedium)
                    Text(info.uid, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Technologies", style = MaterialTheme.typography.labelMedium)
                    Text(info.technologies.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium)
                    info.detail?.let {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
