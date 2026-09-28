package com.add.pepers

import androidx.compose.runtime.Composable

/**
 * First-run workspace setup is optional.
 *
 * The application must never force the user to create a shop, month,
 * assistant, registration number, or working days before entering the app.
 * These records can be created later from the appropriate screens.
 */
@Composable
fun FirstSetupWizard(
    database: Database,
    userName: String,
    completionKey: String,
    onFinished: () -> Unit
) {
    // Intentionally empty. No mandatory first-run dialog is shown.
}
