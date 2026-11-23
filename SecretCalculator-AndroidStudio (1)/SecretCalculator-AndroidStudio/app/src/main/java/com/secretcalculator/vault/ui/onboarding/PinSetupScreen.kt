package com.secretcalculator.vault.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secretcalculator.vault.ui.theme.DarkSurfaceElevated
import com.secretcalculator.vault.ui.theme.ObsidianBlack
import com.secretcalculator.vault.ui.theme.VividOrange

@Composable
fun PinSetupScreen(
    onPinConfirmed: (String) -> Unit
) {
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmStep by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current

    val activePin = if (isConfirmStep) confirmPin else firstPin

    fun appendDigit(digit: String) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        errorMessage = null
        if (isConfirmStep) {
            if (confirmPin.length < 8) confirmPin += digit
        } else {
            if (firstPin.length < 8) firstPin += digit
        }
    }

    fun backspace() {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        errorMessage = null
        if (isConfirmStep) {
            if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
        } else {
            if (firstPin.isNotEmpty()) firstPin = firstPin.dropLast(1)
        }
    }

    fun submitStep() {
        if (!isConfirmStep) {
            if (firstPin.length < 4) {
                errorMessage = "Choose at least 4 digits for your secret PIN."
                return
            }
            isConfirmStep = true
        } else {
            if (confirmPin != firstPin) {
                errorMessage = "PINs do not match. Try again."
                confirmPin = ""
                return
            }
            onPinConfirmed(firstPin)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ObsidianBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = VividOrange,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                AnimatedContent(targetState = isConfirmStep, label = "pin_step") { confirming ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (confirming) "Confirm Secret PIN" else "Create Secret PIN",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (confirming) {
                                "Re-enter your secret PIN to verify."
                            } else {
                                "Enter 4 to 8 digits. Whenever you type this PIN in the calculator and press '=', your encrypted photo vault opens."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFA1A1AA),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Masked PIN dots (never displays plaintext digits)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(24.dp)
                ) {
                    val slotCount = maxOf(4, activePin.length)
                    repeat(slotCount) { idx ->
                        val filled = idx < activePin.length
                        Box(
                            modifier = Modifier
                                .size(if (filled) 14.dp else 10.dp)
                                .clip(CircleShape)
                                .background(if (filled) VividOrange else Color(0xFF3F3F46))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage ?: " ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Custom numeric keypad
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { digit ->
                            SetupKeyButton(
                                label = digit,
                                modifier = Modifier.weight(1f),
                                onClick = { appendDigit(digit) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = {
                            if (isConfirmStep) {
                                isConfirmStep = false
                                confirmPin = ""
                                errorMessage = null
                            } else {
                                firstPin = ""
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    ) {
                        Text(
                            text = if (isConfirmStep) "Back" else "Clear",
                            color = Color(0xFFA1A1AA),
                            fontSize = 16.sp
                        )
                    }

                    SetupKeyButton(
                        label = "0",
                        modifier = Modifier.weight(1f),
                        onClick = { appendDigit("0") }
                    )

                    FilledTonalIconButton(
                        onClick = ::backspace,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = DarkSurfaceElevated,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = ::submitStep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VividOrange,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isConfirmStep) "Activate Private Vault" else "Continue",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupKeyButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(18.dp),
        color = DarkSurfaceElevated
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
