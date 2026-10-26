package com.chatnook.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatnook.app.R
import com.chatnook.app.ui.theme.ChatNookColors

@Composable
fun AuthScreen(viewModel: AuthViewModel, modifier: Modifier = Modifier) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val isSignup = ui.mode == AuthMode.Signup

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChatNookColors.ChatNookGradient)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (isSignup) R.string.auth_create_account else R.string.auth_welcome_back,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = ui.email,
            onValueChange = viewModel::onEmailChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.auth_email_hint)) },
            singleLine = true,
            enabled = !ui.isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = ui.password,
            onValueChange = viewModel::onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.auth_password_hint)) },
            singleLine = true,
            enabled = !ui.isLoading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (isSignup) ImeAction.Next else ImeAction.Done,
            ),
        )
        if (isSignup) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.auth_confirm_password_hint)) },
                singleLine = true,
                enabled = !ui.isLoading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )
        }

        ui.error?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFD4D6),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ui.notice?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD7F5DC),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = viewModel::onEmailPasswordTap,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !ui.isLoading,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = ChatNookColors.Blurple,
            ),
        ) {
            if (ui.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = ChatNookColors.Blurple,
                )
            } else {
                Text(
                    text = stringResource(
                        if (isSignup) R.string.auth_sign_up else R.string.auth_sign_in,
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        TextButton(onClick = { viewModel.selectMode(if (isSignup) AuthMode.Login else AuthMode.Signup) }) {
            Text(
                text = stringResource(
                    if (isSignup) R.string.auth_sign_in_alt else R.string.auth_sign_up_alt,
                ),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(8.dp))
        RowDivider()

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = viewModel::signInAnonymously,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !ui.isLoading,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = stringResource(R.string.auth_anonymous),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.auth_anonymous_desc),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { viewModel.sendMagicLink(ui.email) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !ui.isLoading && ui.email.isNotBlank(),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = stringResource(R.string.auth_magic_link),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.auth_magic_link_desc),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun RowDivider() {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color.White.copy(alpha = 0.3f),
        )
        Text(
            text = stringResource(R.string.auth_or),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color.White.copy(alpha = 0.3f),
        )
    }
}
