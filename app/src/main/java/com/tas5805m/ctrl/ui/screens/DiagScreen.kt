package com.tas5805m.ctrl.ui.screens

import com.tas5805m.ctrl.ui.components.PollWhileStarted
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.tas5805m.ctrl.data.DiagHistory
import com.tas5805m.ctrl.data.DiagReport
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.data.Diagnosis
import com.tas5805m.ctrl.data.FAULT_KEYS
import com.tas5805m.ctrl.data.Finding
import com.tas5805m.ctrl.data.Level
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ColErr = Color(0xFFEF5350)
private val ColWarn = Color(0xFFFFB74D)
private val ColInfo = Color(0xFF4FC3F7)
private val ColOk = Color(0xFF69F0AE)

private fun levelColor(l: Level): Color = when (l) {
    Level.ERR -> ColErr
    Level.WARN -> ColWarn
    Level.INFO -> ColInfo
    Level.OK -> ColOk
}

private fun levelMark(l: Level): String = when (l) {
    Level.ERR -> "✖"
    Level.WARN -> "▲"
    Level.INFO -> "ℹ"
    Level.OK -> "✔"
}

/**
 * Diagnosa: ringkasan masalah otomatis (aturan dari README firmware), status fault chip,
 * info chip, statistik link nirkabel, terminal perintah mentah, dan laporan yang bisa dibagikan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spp = vm.spp
    val ctx = LocalContext.current
    val history by vm.diagHistory.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf("") }
    var viewing by remember { mutableStateOf<DiagReport?>(null) }
    var pendingText by remember { mutableStateOf<String?>(null) }

    // Simpan ke file lewat dialog pilih lokasi Android (tanpa izin storage tambahan).
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val t = pendingText
        pendingText = null
        if (uri != null && t != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(t.toByteArray(Charsets.UTF_8)) }
                    ?: error("stream null")
            }.isSuccess
            notice = if (ok) "Laporan disimpan ke file." else "Gagal menyimpan file."
        }
    }

    fun saveFile(text: String, ms: Long) {
        pendingText = text
        saver.launch(DiagHistory.fileName(ms))
    }

    fun shareText(text: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Laporan diagnosa TAS5805M")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(i, "Bagikan laporan"))
    }

    viewing?.let { r ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(r.title) },
            text = {
                Column {
                    Text(DiagHistory.stamp(r.createdMs), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        r.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("Tutup") } },
            dismissButton = { TextButton(onClick = { shareText(r.text) }) { Text("Bagikan") } },
        )
    }

    val histCard: @Composable () -> Unit = {
        HistoryCard(
            history = history,
            onView = { viewing = it },
            onShare = { shareText(it.text) },
            onSave = { saveFile(it.text, it.createdMs) },
            onDelete = { vm.deleteDiagReport(it.id) },
            onClear = { vm.clearDiagHistory() },
        )
    }

    if (!state.connected) {
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "offline") {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BluetoothDisabled, null, tint = Color.Gray)
                        Spacer(Modifier.height(12.dp))
                        Text("Hubungkan perangkat di tab Koneksi", color = Color.Gray)
                        Text("Riwayat laporan tetap bisa dibuka di bawah.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
            if (notice.isNotEmpty()) item(key = "notice") { Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            item(key = "history") { histCard() }
            item(key = "end") { Spacer(Modifier.height(24.dp)) }
        }
        return
    }

    // Selama layar ini tampil: baca status tiap 2 dtk (fault, counter link, BT). `status` ikut membaca & menghapus fault di chip.
    PollWhileStarted {
        while (true) {
            delay(2000)
            spp.requestStatus()
        }
    }

    val findings = remember(state) { Diagnosis.diagnose(state) }
    var cmd by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "actions") {
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Diagnosa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val win = if (state.baselineAtMs > 0) (System.currentTimeMillis() - state.baselineAtMs) / 1000 else -1L
                    Text(
                        if (win >= 0) "Jendela ukur: $win dtk (counter dihitung sejak Reset ukur)" else "Jendela ukur: belum dimulai",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { vm.refreshAll() }) { Text("Refresh semua") }
                        OutlinedButton(onClick = { spp.requestFault() }) { Text("Baca fault") }
                        // Reset ukur: simpan dulu laporan "sebelum" agar bisa dibandingkan dengan laporan sesudahnya.
                        OutlinedButton(onClick = {
                            vm.saveDiagReport("Sebelum reset ukur", Diagnosis.report(state, findings))
                            notice = "Laporan 'sebelum' disimpan ke riwayat; ukur dimulai ulang."
                            vm.resetDiagnostics()
                        }) { Text("Reset ukur") }
                        OutlinedButton(onClick = {
                            vm.saveDiagReport("Laporan manual", Diagnosis.report(state, findings))
                            notice = "Laporan disimpan ke riwayat."
                        }) { Text("Simpan ke riwayat") }
                        OutlinedButton(onClick = { saveFile(Diagnosis.report(state, findings), System.currentTimeMillis()) }) { Text("Simpan file") }
                        OutlinedButton(onClick = { shareText(Diagnosis.report(state, findings)) }) { Text("Bagikan laporan") }
                    }
                    if (notice.isNotEmpty()) Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        items(findings) { f -> FindingCard(f) }

        item(key = "faults") { FaultCard(state) }

        item(key = "ota") { OtaCard(spp, state.connected) }
        item(key = "history") { histCard() }

        item(key = "chip") { RawCard("Chip & konfigurasi", state, listOf("VER", "INFO", "CLIP", "MOD", "DAC", "DAC2", "EQP", "EQP2")) }

        if (state.hasWsub || state.isWireless) {
            item(key = "wireless") { WirelessRaw(state) }
        }

        item(key = "terminal") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Terminal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cmd,
                            onValueChange = { cmd = it },
                            label = { Text("Perintah (mis. status, fault, help)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { spp.sendRaw(cmd); cmd = "" }),
                        )
                        Button(onClick = { spp.sendRaw(cmd); cmd = "" }) { Text("Kirim") }
                    }
                    Text(
                        "> = perintah terkirim. Baris poll (LVL, WSUB, WSUBS, …) tidak ditampilkan di sini; lihat kartu di atas atau laporan.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                    HorizontalDivider()
                    if (state.lastLines.isEmpty()) {
                        Text("Belum ada log.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        items(state.lastLines.take(60)) { line ->
            Text(
                line,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = when {
                    line.startsWith("ERR") -> ColErr
                    line.startsWith(">") -> ColInfo
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                },
            )
        }

        item(key = "end") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun FindingCard(f: Finding) {
    val c = levelColor(f.level)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = c.copy(alpha = 0.12f)),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(levelMark(f.level), color = c, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(f.title, fontWeight = FontWeight.SemiBold, color = c)
                if (f.detail.isNotEmpty()) Text(f.detail, style = MaterialTheme.typography.bodySmall)
                if (f.action.isNotEmpty()) {
                    Text(
                        "→ " + f.action,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FaultCard(s: AmpState) {
    val kv2 = s.tags["FAULT2"]
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Fault amp (TAS5805M)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            for (chip in 1..2) {
                if (chip == 2 && kv2 == null) continue
                Text("Chip $chip", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (k in FAULT_KEYS) {
                        val active = if (chip == 1) (s.faults[k] ?: 0) == 1 else kv2?.get(k) == "1"
                        AssistChip(
                            onClick = {},
                            label = { Text(Diagnosis.faultLabel(k), fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (active) ColErr.copy(alpha = 0.35f) else Color.Transparent,
                            ),
                        )
                    }
                }
            }
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            if (s.faultHistory.isEmpty()) {
                Text("Riwayat sesi ini: bersih", style = MaterialTheme.typography.bodySmall, color = ColOk)
            } else {
                Text("Riwayat sesi ini:", style = MaterialTheme.typography.labelMedium)
                s.faultHistory.takeLast(8).reversed().forEach { e ->
                    Text(
                        "${fmt.format(Date(e.timeMs))}  chip${e.chip}  ${e.flags.joinToString(",") { Diagnosis.faultLabel(it) }}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RawCard(title: String, s: AmpState, heads: List<String>) {
    val lines = heads.mapNotNull { s.rawByHead[it] }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (lines.isEmpty()) Text("Belum ada data.", style = MaterialTheme.typography.bodySmall)
            lines.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp) }
        }
    }
}

@Composable
private fun WirelessRaw(s: AmpState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Link nirkabel & Bluetooth (mentah)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            listOf("WSUB", "WSUBT", "WSUBC", "BTD").mapNotNull { s.rawByHead[it] }.forEach {
                Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
            }
            s.slaveDiag.forEach { (mac, kv) ->
                Text(
                    "WSUBD $mac " + kv.filterKeys { it != "mac" }.entries.joinToString(" ") { "${it.key}=${it.value}" },
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
                )
            }
            if (s.btEvents.isNotEmpty()) {
                HorizontalDivider()
                Text("Event Bluetooth", style = MaterialTheme.typography.labelMedium)
                s.btEvents.takeLast(8).reversed().forEach {
                    Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryCard(
    history: List<DiagReport>,
    onView: (DiagReport) -> Unit,
    onShare: (DiagReport) -> Unit,
    onSave: (DiagReport) -> Unit,
    onDelete: (DiagReport) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Riwayat laporan (${history.size}/${DiagHistory.MAX})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (history.isNotEmpty()) {
                    TextButton(onClick = { if (confirmClear) { onClear(); confirmClear = false } else confirmClear = true }) {
                        Text(if (confirmClear) "Yakin hapus semua?" else "Hapus semua", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (history.isEmpty()) {
                Text(
                    "Belum ada. Tekan \"Simpan ke riwayat\" atau \"Reset ukur\" (otomatis menyimpan laporan sebelum reset) untuk membandingkan sebelum/sesudah tuning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            history.forEach { r ->
                HorizontalDivider()
                Text(r.title, fontWeight = FontWeight.SemiBold)
                Text(
                    DiagHistory.stamp(r.createdMs) + " · " + r.text.count { it == '\n' } + " baris",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onView(r) }) { Text("Buka") }
                    TextButton(onClick = { onShare(r) }) { Text("Bagikan") }
                    TextButton(onClick = { onSave(r) }) { Text("Simpan file") }
                    TextButton(onClick = { onDelete(r) }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
