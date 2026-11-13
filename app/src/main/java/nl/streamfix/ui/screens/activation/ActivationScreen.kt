package nl.streamfix.ui.screens.activation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import nl.streamfix.R
import nl.streamfix.data.remote.PORTAL_URL
import nl.streamfix.data.remote.PortalResult
import nl.streamfix.data.repository.PortalAccounts
import nl.streamfix.ui.theme.ActivationBackground
import nl.streamfix.ui.tvFocusable
import okhttp3.HttpUrl.Companion.toHttpUrl

@HiltViewModel
class ActivationViewModel @Inject constructor(private val accounts: PortalAccounts) : ViewModel() {
    private val _status = MutableStateFlow<PortalResult?>(null)
    val status = _status.asStateFlow()
    private val _pending = MutableStateFlow<PortalResult.Pending?>(null)
    val pending = _pending.asStateFlow()
    private val mutex = Mutex()
    private var requested = false

    fun refresh() {
        viewModelScope.launch { check(register = true) }
    }

    suspend fun poll() {
        check(register = !requested || _status.value == null)
        while (true) {
            delay(15_000)
            if (_status.value is PortalResult.Pending ||
                (_status.value == PortalResult.Unavailable && _pending.value != null)
            ) check(register = false)
        }
    }

    private suspend fun check(register: Boolean) {
        if (!mutex.tryLock()) return
        try {
            requested = true
            val result = accounts.refresh(register = register, activate = true)
            if (result is PortalResult.Pending) _pending.value = result
            if (result == PortalResult.Blocked) _pending.value = null
            _status.value = result
        } finally {
            mutex.unlock()
        }
    }
}

@Composable
fun ActivationScreen(
    onBack: (() -> Unit)?,
    onActivated: () -> Unit,
    onActivationLost: () -> Unit = {},
    viewModel: ActivationViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.poll() }
    }
    LaunchedEffect(status) {
        if (status is PortalResult.Active && onBack == null) onActivated()
        if (onBack != null && (status is PortalResult.Pending || status == PortalResult.Blocked)) {
            onActivationLost()
        }
    }
    Scaffold(containerColor = ActivationBackground) { insets ->
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(insets).padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        ) {
            val active = status as? PortalResult.Active
            val code = active?.code ?: pending?.code
            val key = active?.key ?: pending?.key
            val details: @Composable ColumnScope.() -> Unit = {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.activation_title), style = MaterialTheme.typography.displaySmall)
                Text(
                    stringResource(R.string.activation_instructions),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (code != null && key != null) {
                    Text("${stringResource(R.string.activation_code)}: $code", color = MaterialTheme.colorScheme.primary)
                    Text("${stringResource(R.string.activation_key)}: $key", color = MaterialTheme.colorScheme.primary)
                    Text("${PORTAL_URL.removePrefix("https://")}/activate")
                }
                val message = when (val current = status) {
                    is PortalResult.Pending -> if (current.playlistCount > 0) {
                        stringResource(R.string.activation_playlists, current.playlistCount)
                    } else stringResource(R.string.activation_waiting)
                    PortalResult.Blocked -> stringResource(R.string.activation_blocked)
                    PortalResult.Unavailable -> stringResource(R.string.activation_unavailable)
                    PortalResult.RateLimited -> stringResource(R.string.activation_rate_limited)
                    is PortalResult.Active -> stringResource(R.string.activation_linked)
                    else -> stringResource(R.string.activation_checking)
                }
                Text(message)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = viewModel::refresh, modifier = Modifier.tvFocusable()) {
                        Text(stringResource(R.string.activation_refresh))
                    }
                    if (onBack != null) {
                        TextButton(onClick = onBack, modifier = Modifier.tvFocusable()) {
                            Text(stringResource(R.string.common_back))
                        }
                    }
                }
            }
            if (maxWidth >= 680.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = details)
                    code?.let { ActivationQr(it) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = {
                    details()
                    code?.let { ActivationQr(it, Modifier.align(Alignment.CenterHorizontally)) }
                })
            }
        }
    }
}

@Composable
private fun ActivationQr(code: String, modifier: Modifier = Modifier) {
    val matrix = remember(code) {
        val url = PORTAL_URL.toHttpUrl().newBuilder().addPathSegment("activate")
            .addQueryParameter("code", code).build().toString()
        QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 1, 1)
    }
    val description = stringResource(R.string.activation_qr)
    Canvas(modifier.size(216.dp).semantics { contentDescription = description }) {
        drawRect(Color.White)
        val cell = (size.minDimension / matrix.width).toInt().coerceAtLeast(1).toFloat()
        val origin = ((size.minDimension - cell * matrix.width) / 2f).toInt().toFloat()
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(
                    Color.Black,
                    topLeft = Offset(origin + x * cell, origin + y * cell),
                    size = Size(cell, cell),
                )
            }
        }
    }
}
