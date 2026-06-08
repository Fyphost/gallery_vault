package com.vaultgallery.app.ui.disguise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A fully functional four-operation calculator. The "=" key first asks the host
 * Activity whether the current entry is a secret unlock code via [onSecretEntered].
 */
@Composable
fun CalculatorScreen(
    onSecretEntered: (String) -> Boolean
) {
    var display by remember { mutableStateOf("0") }
    var expression by remember { mutableStateOf("") }
    // Tracks the raw digit-only entry since the last operator, used as the secret.
    var pendingNumeric by remember { mutableStateOf("") }

    fun onKey(key: String) {
        when (key) {
            "C" -> { display = "0"; expression = ""; pendingNumeric = "" }
            "=" -> {
                // Secret check uses the digits entered so far.
                if (pendingNumeric.isNotEmpty() && onSecretEntered(pendingNumeric)) {
                    return
                }
                val result = evaluate(expression.ifEmpty { display })
                display = result
                expression = result
                pendingNumeric = ""
            }
            "+", "-", "×", "÷" -> {
                expression = (expression.ifEmpty { display }) + key
                display = key
                pendingNumeric = ""
            }
            else -> { // digits and "."
                expression = if (expression == "0") key else expression + key
                pendingNumeric += key
                display = pendingNumeric.ifEmpty { expression }
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.BottomEnd
            ) {
                Text(
                    text = display,
                    fontSize = 64.sp,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            val rows = listOf(
                listOf("C", "÷", "×", "-"),
                listOf("7", "8", "9", "+"),
                listOf("4", "5", "6", "="),
                listOf("1", "2", "3", "0"),
                listOf(".")
            )
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { key ->
                        CalcKey(key, Modifier.weight(1f)) { onKey(key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    val isOperator = label in setOf("÷", "×", "-", "+", "=")
    Button(
        onClick = onClick,
        modifier = modifier
            .padding(vertical = 6.dp)
            .aspectRatio(1f),
        colors = if (isOperator) {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        } else {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        }
    ) {
        Text(label, fontSize = 24.sp)
    }
}

/** Minimal left-to-right evaluator (no precedence) good enough for a calculator UI. */
private fun evaluate(expr: String): String {
    if (expr.isBlank()) return "0"
    return try {
        val tokens = Regex("([+\\-×÷])").split(expr).filter { it.isNotEmpty() }
        val ops = Regex("[+\\-×÷]").findAll(expr).map { it.value }.toList()
        if (tokens.isEmpty()) return "0"
        var acc = tokens[0].toDouble()
        ops.forEachIndexed { i, op ->
            val next = tokens.getOrNull(i + 1)?.toDoubleOrNull() ?: return@forEachIndexed
            acc = when (op) {
                "+" -> acc + next
                "-" -> acc - next
                "×" -> acc * next
                "÷" -> if (next != 0.0) acc / next else return "Error"
                else -> acc
            }
        }
        if (acc == acc.toLong().toDouble()) acc.toLong().toString() else acc.toString()
    } catch (_: Exception) {
        "Error"
    }
}
