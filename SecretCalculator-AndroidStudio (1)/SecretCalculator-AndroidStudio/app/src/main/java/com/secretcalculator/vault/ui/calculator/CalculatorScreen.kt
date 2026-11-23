package com.secretcalculator.vault.ui.calculator

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secretcalculator.vault.data.local.entity.CalcHistoryEntity
import com.secretcalculator.vault.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    isDarkTheme: Boolean,
    onNavigateToVault: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    var showHistorySheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.vaultUnlockEvents.collect {
            onNavigateToVault()
        }
    }

    val backgroundColor = if (isDarkTheme) ObsidianBlack else LightCanvas
    val digitKeyBg = if (isDarkTheme) DarkSurfaceElevated else LightSurface
    val digitKeyFg = if (isDarkTheme) Color.White else TextDarkPrimary
    val funcKeyBg = if (isDarkTheme) Color(0xFFA5A5A5) else LightSurfaceSecondary
    val funcKeyFg = if (isDarkTheme) Color.Black else TextDarkPrimary

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Subtle top utility row — strictly calculator history
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showHistorySheet = true }) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = "Calculation History",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { viewModel.onBackspace() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Numeric display area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = uiState.expressionPreview,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.height(6.dp))
                val fontSize = when {
                    uiState.display.length > 11 -> 42.sp
                    uiState.display.length > 8 -> 54.sp
                    else -> 68.sp
                }
                Text(
                    text = uiState.display,
                    color = if (uiState.isError) DangerCrimson else digitKeyFg,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }

            // Keypad grid
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CalcKey("AC", funcKeyBg, funcKeyFg, Modifier.weight(1f)) { viewModel.onAllClear() }
                    CalcKey("±", funcKeyBg, funcKeyFg, Modifier.weight(1f)) { viewModel.onToggleSign() }
                    CalcKey("%", funcKeyBg, funcKeyFg, Modifier.weight(1f)) { viewModel.onPercent() }
                    CalcKey(
                        "÷",
                        if (uiState.pendingOperator == CalcOperator.DIVIDE && uiState.resetOnNextDigit) Color.White else VividOrange,
                        if (uiState.pendingOperator == CalcOperator.DIVIDE && uiState.resetOnNextDigit) VividOrange else Color.White,
                        Modifier.weight(1f)
                    ) { viewModel.onOperator(CalcOperator.DIVIDE) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CalcKey("7", digitKeyBg, digitKeyFg, Modifier.weight(1f)) { viewModel.onDigit("7") }
                    CalcKey("8", digitKeyBg, digitKeyFg, Modifier.weight(1f)) { viewModel.onDigit("8") }
                    CalcKey("9", digitKeyBg, digitKeyFg, Modifier.weight(1f)) { viewModel.onDigit("9") }
                    CalcKey(
                        "×",
                        if (uiState.pendingOperator == CalcOperator.MULTIPLY && uiState.resetOnNextDigit) Color.White else VividOrange,
                        if (uiState.pendingOperator == CalcOperator.MULTIPLY && uiState.resetOnNextDigit) VividOrange else Color.White,
                        Modifier.weight(1f)
                    ) { viewModel.onOperator(CalcOperator.MULTIPLY) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CalcKey("4", digitKeyBg, digitKeyFg, Modifier.weight(1f)) { viewModel.onDigit("4") }
                    CalcKey("5", digitKeyBg, digitKeyFg, Modifier.weight(1f)) { viewModel.onDigit("5") }
                    CalcKey("6", digitKeyBg, digitKeyFg, Modifier.weight(1f)) { viewModel.onDigit("6") }
                    CalcKey(
                        "−",
                        if (uiState.pendingOperator == CalcOperator.SUBTRACT && uiState.resetOnNextDigit) Color.White else VividOrange,
                        if (uiState.pendingOperator == CalcOperator.SUBTRACT && uiState.resetOnNextDigit) VividOrange else Color.White,
                        Modifier.weight(1f)
                    ) { viewModel.onOperator(CalcOperator.SUBTRACT) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CalcKey("1", digitKeyBg, digitKeyFg, Modifier.weight(1f)) { viewModel.onDigit("1") }
                    CalcKey("2", digitKeyBg, digitKeyFg, Modifier.weight(1f)) { viewModel.onDigit("2") }
                    CalcKey("3", digitKeyBg, digitKeyFg, Modifier.weight(1f)) { viewModel.onDigit("3") }
                    CalcKey(
                        "+",
                        if (uiState.pendingOperator == CalcOperator.ADD && uiState.resetOnNextDigit) Color.White else VividOrange,
                        if (uiState.pendingOperator == CalcOperator.ADD && uiState.resetOnNextDigit) VividOrange else Color.White,
                        Modifier.weight(1f)
                    ) { viewModel.onOperator(CalcOperator.ADD) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CalcKey("0", digitKeyBg, digitKeyFg, Modifier.weight(2.08f), isWide = true) { viewModel.onDigit("0") }
                    CalcKey(".", digitKeyBg, digitKeyFg, Modifier.weight(1f)) { viewModel.onDecimal() }
                    CalcKey("=", VividOrange, Color.White, Modifier.weight(1f)) { viewModel.onEquals() }
                }
            }
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CalculationHistorySheet(
                history = history,
                onSelect = {
                    viewModel.applyHistoryResult(it.result)
                    showHistorySheet = false
                },
                onClear = { viewModel.clearHistory() }
            )
        }
    }
}

@Composable
private fun CalcKey(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "calc_key_scale"
    )
    val haptics = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .scale(scale)
            .then(
                if (isWide) Modifier.height(78.dp) else Modifier.aspectRatio(1f)
            )
            .clip(if (isWide) RoundedCornerShape(40.dp) else CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White.copy(alpha = 0.25f))
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        color = containerColor,
        shape = if (isWide) RoundedCornerShape(40.dp) else CircleShape
    ) {
        Box(
            contentAlignment = if (isWide) Alignment.CenterStart else Alignment.Center,
            modifier = if (isWide) Modifier.padding(start = 30.dp) else Modifier
        ) {
            Text(
                text = label,
                color = contentColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CalculationHistorySheet(
    history: List<CalcHistoryEntity>,
    onSelect: (CalcHistoryEntity) -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Calculation History",
                style = MaterialTheme.typography.titleMedium
            )
            if (history.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Clear history")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (history.isEmpty()) {
            Text(
                text = "No calculations recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history, key = { it.id }) { item ->
                    Surface(
                        onClick = { onSelect(item) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.expression,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "= ${item.result}",
                                style = MaterialTheme.typography.titleMedium,
                                color = VividOrange
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
