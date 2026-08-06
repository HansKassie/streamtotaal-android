package nl.streamfix.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.streamfix.R
import nl.streamfix.ui.LocalIsTv
import nl.streamfix.ui.dpadExitField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XtreamLoginScreen(
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    viewModel: XtreamLoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val isTv = LocalIsTv.current
    val fieldSpacing = if (isTv) 12.dp else 16.dp
    val actionSpacing = if (isTv) 20.dp else 32.dp

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onLoggedIn()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
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
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 24.dp,
                    vertical = if (isTv) 16.dp else 24.dp,
                ),
            verticalArrangement = Arrangement.Top,
        ) {
            if (state.providers.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.login_choose_provider_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                var providerMenuOpen by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { providerMenuOpen = true },
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = when {
                                state.useCustomProvider ->
                                    stringResource(R.string.login_custom_provider)
                                else -> state.selectedProvider?.name
                                    ?: stringResource(
                                        R.string.login_choose_provider_placeholder,
                                    )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = providerMenuOpen,
                        onDismissRequest = { providerMenuOpen = false },
                    ) {
                        state.providers.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.name) },
                                onClick = {
                                    providerMenuOpen = false
                                    viewModel.onSelectProvider(provider)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.login_add_custom_provider))
                            },
                            onClick = {
                                providerMenuOpen = false
                                viewModel.onSelectCustomProvider()
                            },
                        )
                    }
                }
                Spacer(Modifier.height(fieldSpacing))
            }

            if (state.useCustomProvider) {
                OutlinedTextField(
                    value = state.customName,
                    onValueChange = viewModel::onCustomNameChange,
                    label = { Text(stringResource(R.string.login_provider_name)) },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                    modifier = Modifier
                        .dpadExitField(focusManager)
                        .fillMaxWidth(),
                )
                Spacer(Modifier.height(fieldSpacing))
                OutlinedTextField(
                    value = state.customUrl,
                    onValueChange = viewModel::onCustomUrlChange,
                    label = { Text(stringResource(R.string.login_server_url)) },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                    modifier = Modifier
                        .dpadExitField(focusManager)
                        .fillMaxWidth(),
                )
                Spacer(Modifier.height(fieldSpacing))
            }
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text(stringResource(R.string.login_username)) },
                singleLine = true,
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
                modifier = Modifier
                    .dpadExitField(focusManager)
                    .fillMaxWidth(),
            )
            Spacer(Modifier.height(fieldSpacing))
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(stringResource(R.string.login_password)) },
                singleLine = true,
                enabled = !state.isLoading,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = stringResource(
                                if (passwordVisible) R.string.login_password_hide
                                else R.string.login_password_show,
                            ),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (state.canSubmit) viewModel.submit()
                    },
                ),
                modifier = Modifier
                    .dpadExitField(focusManager)
                    .fillMaxWidth(),
            )

            state.errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(actionSpacing))
            Button(
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.login_submit))
                }
            }
        }
    }
}
