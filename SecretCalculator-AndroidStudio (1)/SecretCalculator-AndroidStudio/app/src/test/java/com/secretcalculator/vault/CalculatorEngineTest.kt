package com.secretcalculator.vault

import com.secretcalculator.vault.ui.calculator.CalcOperator
import com.secretcalculator.vault.ui.calculator.CalculatorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class CalculatorEngineTest {

    @Test
    fun additionAndFormatting_areAccurate() {
        val result = CalculatorViewModel.compute(
            BigDecimal("12.5"),
            BigDecimal("7.5"),
            CalcOperator.ADD
        )
        assertEquals("20", CalculatorViewModel.formatNumber(result!!))
    }

    @Test
    fun divisionByZero_returnsNullError() {
        val result = CalculatorViewModel.compute(
            BigDecimal("42"),
            BigDecimal.ZERO,
            CalcOperator.DIVIDE
        )
        assertNull(result)
    }

    @Test
    fun multiplicationWithDecimals_stripsTrailingZeros() {
        val result = CalculatorViewModel.compute(
            BigDecimal("2.5"),
            BigDecimal("4"),
            CalcOperator.MULTIPLY
        )
        assertEquals("10", CalculatorViewModel.formatNumber(result!!))
    }
}
