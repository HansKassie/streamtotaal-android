package nl.streamfix.ui.screens.connectiontest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.streamfix.ui.tvFocusable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionTestScreen(
    onBack: () -> Unit,
    viewModel: ConnectionTestViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verbinding testen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Terug",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                text = "Controleert of je provider bereikbaar is, je inlog " +
                    "geldig is en de onderdelen beschikbaar zijn.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(16.dp))

            state.rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (row.state) {
                            CheckState.Running -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            CheckState.Ok -> Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF3DBE6E),
                            )
                            CheckState.Failed -> Icon(
                                Icons.Filled.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            CheckState.Pending -> Text(
                                text = "•",
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.label,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (row.message.isNotBlank()) {
                            Text(
                                text = row.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (row.state == CheckState.Failed) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = viewModel::runChecks,
                enabled = !state.running,
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusable(),
            ) {
                Text(if (state.running) "Bezig met testen..." else "Opnieuw testen")
            }
        }
    }
}
