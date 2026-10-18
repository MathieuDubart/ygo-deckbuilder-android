package dev.ygo.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.ygo.app.core.l10n.t
import dev.ygo.app.core.state.AppState
import dev.ygo.app.ui.theme.Theme
import kotlinx.coroutines.launch

/** Connexion à l'instance choisie. L'inscription suit le même chemin, un bouton plus loin. */
@Composable
fun LoginScreen(app: AppState) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        busy = true
        error = null
        scope.launch {
            try {
                val user = if (registering) {
                    app.endpoints.register(email, username, password)
                } else {
                    app.endpoints.login(email, password)
                }
                app.signedIn(user)
            } catch (e: Exception) {
                error = e.message ?: t("android.errors.generic")
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            t(if (registering) "auth.register.title" else "auth.login.title"),
            style = MaterialTheme.typography.headlineSmall,
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(t("auth.fields.email")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        if (registering) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(t("auth.fields.username")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(t("auth.fields.password")) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Theme.danger)
        }
        Button(
            onClick = ::submit,
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t(if (registering) "auth.register.submit" else "auth.login.submit"))
        }
        TextButton(onClick = { registering = !registering }, modifier = Modifier.fillMaxWidth()) {
            Text(t(if (registering) "auth.register.haveAccount" else "auth.login.noAccount"))
        }
    }
}
