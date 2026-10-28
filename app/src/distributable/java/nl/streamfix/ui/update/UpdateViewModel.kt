package nl.streamfix.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.streamfix.domain.model.UpdateInfo
import nl.streamfix.domain.usecase.CheckForUpdateUseCase

@HiltViewModel
class UpdateViewModel @Inject constructor(
    checkForUpdate: CheckForUpdateUseCase,
) : ViewModel() {

    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update.asStateFlow()

    private val _dismissed = MutableStateFlow(false)
    val dismissed: StateFlow<Boolean> = _dismissed.asStateFlow()

    init {
        viewModelScope.launch {
            _update.value = runCatching { checkForUpdate() }.getOrNull()
        }
    }

    fun dismiss() {
        _dismissed.value = true
    }
}
