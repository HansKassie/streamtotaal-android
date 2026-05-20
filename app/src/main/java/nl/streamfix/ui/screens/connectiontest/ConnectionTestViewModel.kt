package nl.streamfix.ui.screens.connectiontest

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.streamfix.R
import nl.streamfix.domain.usecase.GetLiveCategoriesUseCase
import nl.streamfix.domain.usecase.GetSeriesCategoriesUseCase
import nl.streamfix.domain.usecase.GetVodCategoriesUseCase
import nl.streamfix.domain.usecase.VerifyActiveAccountUseCase
import nl.streamfix.domain.util.AppResult
import nl.streamfix.ui.uiMessage

enum class CheckState { Pending, Running, Ok, Failed }

data class CheckRow(
    val label: String,
    val state: CheckState = CheckState.Pending,
    val message: String = "",
)

data class ConnectionTestUiState(
    val rows: List<CheckRow> = emptyList(),
    val running: Boolean = false,
)

@HiltViewModel
class ConnectionTestViewModel @Inject constructor(
    private val verifyAccount: VerifyActiveAccountUseCase,
    private val getLiveCategories: GetLiveCategoriesUseCase,
    private val getVodCategories: GetVodCategoriesUseCase,
    private val getSeriesCategories: GetSeriesCategoriesUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionTestUiState(rows = initialRows()))
    val state: StateFlow<ConnectionTestUiState> = _state.asStateFlow()

    init {
        runChecks()
    }

    fun runChecks() {
        if (_state.value.running) return
        _state.value = ConnectionTestUiState(rows = initialRows(), running = true)
        viewModelScope.launch {
            step(0) {
                when (val r = verifyAccount()) {
                    is AppResult.Success ->
                        ok(context.getString(R.string.conn_check_subscription_active))
                    is AppResult.Failure -> fail(r.error.uiMessage(context))
                }
            }
            step(1) { categoryResult(getLiveCategories()) }
            step(2) { categoryResult(getVodCategories()) }
            step(3) { categoryResult(getSeriesCategories()) }
            _state.update { it.copy(running = false) }
        }
    }

    private fun initialRows(): List<CheckRow> = listOf(
        CheckRow(context.getString(R.string.conn_step_server_login)),
        CheckRow(context.getString(R.string.conn_step_live_tv)),
        CheckRow(context.getString(R.string.conn_step_movies)),
        CheckRow(context.getString(R.string.conn_step_series)),
    )

    private fun ok(msg: String) = CheckRow("", CheckState.Ok, msg)
    private fun fail(msg: String) = CheckRow("", CheckState.Failed, msg)

    private fun <T> categoryResult(r: AppResult<List<T>>): CheckRow = when (r) {
        is AppResult.Success ->
            if (r.data.isEmpty()) {
                ok(context.getString(R.string.conn_check_reachable_no_categories))
            } else {
                ok(context.getString(R.string.conn_check_available))
            }
        is AppResult.Failure -> fail(r.error.uiMessage(context))
    }

    private suspend fun step(index: Int, block: suspend () -> CheckRow) {
        setState(index) { it.copy(state = CheckState.Running, message = "") }
        val outcome = block()
        setState(index) {
            it.copy(state = outcome.state, message = outcome.message)
        }
    }

    private fun setState(index: Int, transform: (CheckRow) -> CheckRow) {
        _state.update { s ->
            s.copy(
                rows = s.rows.mapIndexed { i, row ->
                    if (i == index) transform(row) else row
                },
            )
        }
    }
}
