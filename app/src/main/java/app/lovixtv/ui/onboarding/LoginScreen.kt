/*
 * LOVIX login screen.
 *
 * On first launch the user sees this screen instead of the full source setup form.
 * They enter their username and password for the built-in LOVIX provider.
 */
package app.lovixtv.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.lovixtv.R
import app.lovixtv.core.LovixDefaults
import app.lovixtv.ui.SourcesViewModel

/**
 * Simplified login screen for first-run users.
 *
 * Shows only username and password fields with a Sign In button.
 * An "Add custom source" link navigates to the full AddSourceScreen for advanced users.
 */
@Composable
fun LoginScreen(
    viewModel: SourcesViewModel,
    onLoggedIn: () -> Unit,
    onAddCustomSource: () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    // Use the default (first) LOVIX provider automatically
    val provider = LovixDefaults.providers[0]

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var testMessage by remember { mutableStateOf<String?>(null) }
    var testError by remember { mutableStateOf<String?>(null) }

    val canSubmit = username.isNotBlank() && password.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 400.dp)) {
            // App title
            Text(
                "LOVIX",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Sign in to start watching",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))

            // Username field
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.recset_field_username)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            // Password field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.recset_field_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            // Sign In button
            Button(
                onClick = {
                    val source = LovixDefaults.sourceFor(
                        provider,
                        username,
                        password,
                    )
                    viewModel.saveAndSync(source) { success ->
                        if (success) onLoggedIn()
                    }
                },
                enabled = canSubmit && !ui.testing && !ui.syncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (ui.syncing) "Loading…"
                    else "Sign In"
                )
            }

            Spacer(Modifier.height(8.dp))

            // Test connection button
            OutlinedButton(
                onClick = {
                    val source = LovixDefaults.sourceFor(
                        provider,
                        username,
                        password,
                    )
                    viewModel.test(source)
                    // Capture test result from UI state
                    testMessage = ui.testResult
                    testError = ui.testError
                },
                enabled = canSubmit && !ui.testing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (ui.testing) {
                    CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.recset_test_connection))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Test results
            testMessage?.let { message ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Text(
                        message,
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            testError?.let { message ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        message,
                        Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            ui.syncMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Loading indicator during sync
            if (ui.syncing) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.height(16.dp), strokeWidth = 2.dp)
                    Text(
                        "Connecting…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(24.dp))

            // Add custom source link
            TextButton(
                onClick = onAddCustomSource,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.height(4.dp))
                Text("Add custom source")
            }

            Spacer(Modifier.height(20.dp))

            // Privacy note
            Text(
                stringResource(R.string.onboarding_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
