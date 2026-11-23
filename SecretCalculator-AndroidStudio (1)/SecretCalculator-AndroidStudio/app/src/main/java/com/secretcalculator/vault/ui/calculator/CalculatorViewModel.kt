package com.secretcalculator.vault.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.secretcalculator.vault.data.local.entity.CalcHistoryEntity
import com.secretcalculator.vault.data.repository.VaultRepository
import com.secretcalculator.vault.security.PinManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

enum class CalcOperator(val symbol: String) {
    ADD("+"),
    SUBTRACT("−"),
    MULTIPLY("×"),
    DIVIDE("÷")
}

data class CalculatorUiState(
    val display: String = "0",
    val expressionPreview: String = "",
    val accumulator: BigDecimal? = null,
    val pendingOperator: CalcOperator? = null,
    val resetOnNextDigit: Boolean = false,
    val rawDigitBuffer: String = "0",
    val hasOperatorInSession: Boolean = false,
    val isError: Boolean = false
)

class CalculatorViewModel(
    private val repository: VaultRepository,
    private val pinManager: PinManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<CalcHistoryEntity>> = repository.calcHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _vaultUnlockEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val vaultUnlockEvents = _vaultUnlockEvents.asSharedFlow()

    fun onDigit(digit: String) {
        _uiState.update { state ->
            if (state.isError) {
                return@update CalculatorUiState(
                    display = digit,
                    rawDigitBuffer = digit
                )
            }
            val current = if (state.resetOnNextDigit || state.display == "0") "" else state.display
            if (current.length >= 15) return@update state
            val nextDisplay = current + digit
            val nextBuffer = if (!state.hasOperatorInSession) nextDisplay else ""
            state.copy(
                display = nextDisplay,
                rawDigitBuffer = nextBuffer,
                resetOnNextDigit = false
            )
        }
    }

    fun onDecimal() {
        _uiState.update { state ->
            if (state.isError) {
                return@update CalculatorUiState(display = "0.", rawDigitBuffer = "")
            }
            if (state.resetOnNextDigit) {
                return@update state.copy(
                    display = "0.",
                    resetOnNextDigit = false,
                    rawDigitBuffer = ""
                )
            }
            if (state.display.contains(".")) return@update state
            state.copy(
                display = state.display + ".",
                rawDigitBuffer = ""
            )
        }
    }

    fun onToggleSign() {
        _uiState.update { state ->
            if (state.isError || state.display == "0") return@update state
            val toggled = if (state.display.startsWith("-")) {
                state.display.removePrefix("-")
            } else {
                "-${state.display}"
            }
            state.copy(display = toggled, rawDigitBuffer = "")
        }
    }

    fun onPercent() {
        _uiState.update { state ->
            if (state.isError) return@update state
            val current = state.display.toBigDecimalOrNull() ?: return@update state
            val result = current.divide(BigDecimal(100), MathContext.DECIMAL64)
            state.copy(
                display = formatNumber(result),
                rawDigitBuffer = "",
                resetOnNextDigit = true
            )
        }
    }

    fun onBackspace() {
        _uiState.update { state ->
            if (state.isError || state.resetOnNextDigit) {
                return@update state.copy(display = "0", isError = false, rawDigitBuffer = "0")
            }
            val next = if (state.display.length <= 1 || (state.display.length == 2 && state.display.startsWith("-"))) {
                "0"
            } else {
                state.display.dropLast(1)
            }
            state.copy(
                display = next,
                rawDigitBuffer = if (!state.hasOperatorInSession) next else ""
            )
        }
    }

    fun onAllClear() {
        _uiState.value = CalculatorUiState()
    }

    fun onOperator(op: CalcOperator) {
        _uiState.update { state ->
            if (state.isError) return@update state
            val currentVal = state.display.toBigDecimalOrNull() ?: BigDecimal.ZERO

            if (state.accumulator != null && state.pendingOperator != null && !state.resetOnNextDigit) {
                val computed = compute(state.accumulator, currentVal, state.pendingOperator)
                if (computed == null) {
                    return@update CalculatorUiState(display = "Error", isError = true)
                }
                val formatted = formatNumber(computed)
                state.copy(
                    display = formatted,
                    accumulator = computed,
                    pendingOperator = op,
                    expressionPreview = "$formatted ${op.symbol}",
                    resetOnNextDigit = true,
                    hasOperatorInSession = true,
                    rawDigitBuffer = ""
                )
            } else {
                val formatted = formatNumber(currentVal)
                state.copy(
                    accumulator = currentVal,
                    pendingOperator = op,
                    expressionPreview = "$formatted ${op.symbol}",
                    resetOnNextDigit = true,
                    hasOperatorInSession = true,
                    rawDigitBuffer = ""
                )
            }
        }
    }

    fun onEquals() {
        val state = _uiState.value

        // Secret PIN check: only when pure digits were entered (no active arithmetic expression)
        if (!state.hasOperatorInSession &&
            state.rawDigitBuffer.isNotEmpty() &&
            pinManager.verifyPin(state.rawDigitBuffer)
        ) {
            // Reset calculator cleanly behind the scenes so PIN never lingers on screen
            _uiState.value = CalculatorUiState()
            _vaultUnlockEvents.tryEmit(Unit)
            return
        }

        if (state.isError || state.accumulator == null || state.pendingOperator == null) {
            return
        }

        val rhs = state.display.toBigDecimalOrNull() ?: return
        val result = compute(state.accumulator, rhs, state.pendingOperator)
        if (result == null) {
            _uiState.value = CalculatorUiState(
                display = "Error",
                expressionPreview = "${formatNumber(state.accumulator)} ${state.pendingOperator.symbol} ${formatNumber(rhs)}",
                isError = true
            )
            return
        }

        val expr = "${formatNumber(state.accumulator)} ${state.pendingOperator.symbol} ${formatNumber(rhs)}"
        val formattedResult = formatNumber(result)

        _uiState.value = CalculatorUiState(
            display = formattedResult,
            expressionPreview = "$expr =",
            resetOnNextDigit = true
        )

        viewModelScope.launch {
            repository.saveCalculation(expr, formattedResult)
        }
    }

    fun applyHistoryResult(result: String) {
        _uiState.value = CalculatorUiState(
            display = result,
            resetOnNextDigit = true
        )
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearCalculationHistory()
        }
    }

    companion object {
        fun compute(a: BigDecimal, b: BigDecimal, op: CalcOperator): BigDecimal? {
            return try {
                when (op) {
                    CalcOperator.ADD -> a.add(b, MathContext.DECIMAL64)
                    CalcOperator.SUBTRACT -> a.subtract(b, MathContext.DECIMAL64)
                    CalcOperator.MULTIPLY -> a.multiply(b, MathContext.DECIMAL64)
                    CalcOperator.DIVIDE -> {
                        if (b.compareTo(BigDecimal.ZERO) == 0) null
                        else a.divide(b, 10, RoundingMode.HALF_UP).stripTrailingZeros()
                    }
                }
            } catch (_: ArithmeticException) {
                null
            }
        }

        fun formatNumber(value: BigDecimal): String {
            val stripped = value.stripTrailingZeros()
            val plain = if (stripped.scale() <= 0) {
                stripped.toBigInteger().toString()
            } else {
                stripped.toPlainString()
            }
            return if (plain.length > 14) {
                value.round(MathContext(9, RoundingMode.HALF_UP)).toString()
            } else {
                plain
            }
        }

        fun factory(repository: VaultRepository, pinManager: PinManager): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CalculatorViewModel(repository, pinManager) as T
                }
            }
        }
    }
}
