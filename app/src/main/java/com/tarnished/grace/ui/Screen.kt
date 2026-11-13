package com.tarnished.grace.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tarnished.grace.Core
import com.tarnished.grace.data.NodeView
import com.tarnished.grace.data.Phase
import com.tarnished.grace.data.VlessConfig

@Composable
fun GraceScreen(onToggle: (Boolean) -> Unit) {
    val state by Core.engine.state.collectAsState()
    var dialog by remember { mutableStateOf(false) }
    var links by remember { mutableStateOf(Core.store.links) }
    var subscription by remember { mutableStateOf(Core.store.subscription) }

    val stored = remember(links) { VlessConfig.parseAll(links).map { NodeView(it.name, null, true, false) } }
    val shown = if (state.phase == Phase.OFF || state.nodes.isEmpty()) stored else state.nodes
    val reachable = shown.count { it.alive }

    val status = when (state.phase) {
        Phase.OFF -> "La grâce sommeille"
        Phase.STARTING -> "La grâce s'éveille…"
        Phase.ON -> if (state.activeName != null) "Guidé par la grâce" else "À la recherche d'un guide…"
    }
    val detail = if (state.phase == Phase.ON && state.activeName != null) {
        "${state.activeName} · ${state.activeMs ?: 0} ms"
    } else {
        null
    }
    val label = when (state.phase) {
        Phase.OFF -> "ALLUMER LA GRÂCE"
        Phase.STARTING -> "ÉVEIL EN COURS"
        Phase.ON -> "ÉTEINDRE LA GRÂCE"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Grace.Ink, Color(0xFF15110A), Grace.Ink)))
    ) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Text(
                        text = "LES TERRES INTERMÉDIAIRES",
                        style = MaterialTheme.typography.labelSmall,
                        color = Grace.Ash,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "GRACE TUNNEL",
                        style = MaterialTheme.typography.titleLarge,
                        color = Grace.Gold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    OrnateDivider()
                }
                item {
                    Spacer(Modifier.height(18.dp))
                    GraceOrb(
                        on = state.phase == Phase.ON,
                        busy = state.phase == Phase.STARTING,
                        diameter = 230.dp,
                        onClick = { onToggle(state.phase != Phase.OFF) }
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state.phase == Phase.ON) Grace.GoldBright else Grace.Gold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Grace.Ash,
                        textAlign = TextAlign.Center
                    )
                    if (detail != null) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Grace.Bone,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                }
                if (state.allDead) {
                    item { LostBanner() }
                }
                state.message?.let { text ->
                    item {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Grace.Bone,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Grace.Blood, RoundedCornerShape(4.dp))
                                .background(Color(0x33A32121))
                                .padding(14.dp)
                        )
                    }
                }
                item {
                    SectionTitle("SITES DE GRÂCE", "$reachable/${shown.size} joignables")
                }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            text = "Aucun site de grâce découvert.\nAjoutez vos liens VLESS ci-dessous.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Grace.Ash,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    }
                }
                items(shown) { node ->
                    NodeCard(node)
                }
            }
            GoldButton(
                text = "AJOUTER DES CONFIGS",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) { dialog = true }
        }
    }

    if (dialog) {
        AddDialog(
            links = links,
            subscription = subscription,
            onSave = { newLinks, newSub ->
                Core.store.links = newLinks.trim()
                Core.store.subscription = newSub.trim()
                links = newLinks.trim()
                subscription = newSub.trim()
                dialog = false
            },
            onDismiss = { dialog = false }
        )
    }
}

@Composable
private fun AddDialog(
    links: String,
    subscription: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var linksText by remember { mutableStateOf(links) }
    var subText by remember { mutableStateOf(subscription) }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Grace.Gold,
        unfocusedBorderColor = Grace.GoldDim,
        focusedTextColor = Grace.Bone,
        unfocusedTextColor = Grace.Bone,
        cursorColor = Grace.Gold,
        focusedLabelColor = Grace.Gold,
        unfocusedLabelColor = Grace.Ash
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Grace.Panel,
        shape = RoundedCornerShape(6.dp),
        title = {
            Text(text = "SITES DE GRÂCE", style = MaterialTheme.typography.titleMedium, color = Grace.Gold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = linksText,
                    onValueChange = { linksText = it },
                    label = { Text("Liens vless:// (un par ligne)") },
                    minLines = 4,
                    maxLines = 8,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subText,
                    onValueChange = { subText = it },
                    label = { Text("URL d'abonnement (facultatif)") },
                    singleLine = true,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(linksText, subText) }) {
                Text(text = "GRAVER", style = MaterialTheme.typography.labelLarge, color = Grace.GoldBright)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "ANNULER", style = MaterialTheme.typography.labelLarge, color = Grace.Ash)
            }
        }
    )
}
