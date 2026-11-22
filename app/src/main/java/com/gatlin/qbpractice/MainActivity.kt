@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.gatlin.qbpractice

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { QbTheme { App() } }
    }
}

@Composable
fun QbTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

@Composable
fun App(vm: QbViewModel = viewModel()) {
    val ui = vm.ui
    var showSettings by remember { mutableStateOf(false) }
    val showAnswer = ui.mode == Mode.Moderator || ui.revealed

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QB Practice") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Subjects and difficulty")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mode.entries.forEach { m ->
                    ToggleButton(
                        checked = ui.mode == m,
                        onCheckedChange = { vm.setMode(m) },
                        modifier = Modifier.weight(1f),
                    ) { Text(m.label) }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val q = ui.current
                when {
                    ui.loading -> LoadingIndicator(Modifier.align(Alignment.Center))
                    ui.error != null -> Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(ui.error, style = MaterialTheme.typography.bodyLarge)
                        FilledTonalButton(onClick = vm::retry) { Text("Retry") }
                    }
                    q != null -> Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = {}, label = { Text(q.category) })
                            AssistChip(onClick = {}, label = { Text(q.subcategory) })
                            AssistChip(onClick = {}, label = { Text(QbApi.DIFFICULTIES[q.difficulty] ?: "Level ${q.difficulty}") })
                        }
                        Card(
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            SelectionContainer {
                                Text(
                                    q.question,
                                    Modifier.padding(20.dp),
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f),
                                )
                            }
                        }
                        if (showAnswer) {
                            Card(
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            ) {
                                Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Answer", style = MaterialTheme.typography.labelLarge)
                                    SelectionContainer { Text(q.answer, style = MaterialTheme.typography.headlineSmall) }
                                }
                            }
                        } else {
                            FilledTonalButton(onClick = vm::reveal, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                                Text("Show answer")
                            }
                        }
                        if (q.setName.isNotEmpty()) {
                            Text(
                                "${q.year} ${q.setName}".trim(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = vm::next,
                enabled = !ui.loading,
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text("Next question")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false; vm.applyFilters() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Subjects", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::selectAllCategories) { Text("Select all") }
                    }
                }
                items(QbApi.CATEGORIES) { c ->
                    CheckRow(c, c in ui.categories) { vm.toggleCategory(c) }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Difficulty", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::selectAllDifficulties) { Text("Select all") }
                    }
                }
                items(QbApi.DIFFICULTIES.entries.toList()) { (n, label) ->
                    CheckRow("$n · $label", n in ui.difficulties) { vm.toggleDifficulty(n) }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
