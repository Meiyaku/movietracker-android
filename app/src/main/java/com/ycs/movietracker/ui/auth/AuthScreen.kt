package com.ycs.movietracker.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ycs.movietracker.R
import com.ycs.movietracker.ui.theme.ErrorBackground
import com.ycs.movietracker.ui.theme.ErrorText
import com.ycs.movietracker.ui.theme.appColors

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColors.topBarBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App title
            Text(
                text = stringResource(R.string.app_title),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(24.dp))

            // Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Tab row
                    val tabLabels = listOf(
                        stringResource(R.string.tab_log_in),
                        stringResource(R.string.tab_sign_up)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        tabLabels.forEachIndexed { index, label ->
                            val isSelected = selectedTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                TextButton(
                                    onClick = { selectedTab = index; viewModel.clearError() },
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(0.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .align(Alignment.BottomCenter)
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()

                    // Form
                    when (selectedTab) {
                        0 -> LogInTab(
                            uiState = uiState,
                            onSignIn = viewModel::signIn,
                            onFieldChange = viewModel::clearError,
                            onForgotPassword = { showForgotPasswordDialog = true }
                        )
                        1 -> SignUpTab(
                            uiState = uiState,
                            onSignUp = viewModel::signUp,
                            onFieldChange = viewModel::clearError
                        )
                    }
                }
            }
        }
    }

    if (showForgotPasswordDialog) {
        ForgotPasswordDialog(
            uiState = uiState,
            onSendReset = viewModel::sendPasswordReset,
            onDismiss = {
                if (uiState.passwordResetSent) viewModel.dismissPasswordReset()
                showForgotPasswordDialog = false
            }
        )
    }
}

@Composable
private fun SignUpTab(
    uiState: AuthUiState,
    onSignUp: (email: String, password: String, confirmPassword: String) -> Unit,
    onFieldChange: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    val passwordsDoNotMatch = stringResource(R.string.error_passwords_do_not_match)
    val localError = if (confirmPassword.isNotEmpty() && password != confirmPassword) {
        passwordsDoNotMatch
    } else null
    val displayError = localError ?: uiState.signUpError

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (displayError != null) {
            ErrorBox(displayError)
        }

        AuthField(
            label = stringResource(R.string.label_email),
            value = email,
            onValueChange = { email = it; onFieldChange() },
            keyboardType = KeyboardType.Email
        )
        AuthField(
            label = stringResource(R.string.label_password),
            value = password,
            onValueChange = { password = it; onFieldChange() },
            keyboardType = KeyboardType.Password,
            isPassword = true,
            passwordVisible = showPassword,
            onTogglePasswordVisibility = { showPassword = !showPassword }
        )
        AuthField(
            label = stringResource(R.string.label_confirm_password),
            value = confirmPassword,
            onValueChange = { confirmPassword = it; onFieldChange() },
            keyboardType = KeyboardType.Password,
            isPassword = true,
            passwordVisible = showConfirm,
            onTogglePasswordVisibility = { showConfirm = !showConfirm }
        )

        Button(
            onClick = { onSignUp(email, password, confirmPassword) },
            enabled = email.isNotBlank() && password.isNotBlank() && confirmPassword.isNotBlank()
                    && !uiState.isLoading && localError == null,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.authButton),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.action_sign_up), style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}

@Composable
private fun LogInTab(
    uiState: AuthUiState,
    onSignIn: (email: String, password: String) -> Unit,
    onFieldChange: () -> Unit,
    onForgotPassword: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (uiState.signInError != null) {
            ErrorBox(uiState.signInError)
        }

        AuthField(
            label = stringResource(R.string.label_email),
            value = email,
            onValueChange = { email = it; onFieldChange() },
            keyboardType = KeyboardType.Email
        )
        AuthField(
            label = stringResource(R.string.label_password),
            value = password,
            onValueChange = { password = it; onFieldChange() },
            keyboardType = KeyboardType.Password,
            isPassword = true,
            passwordVisible = showPassword,
            onTogglePasswordVisibility = { showPassword = !showPassword }
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = onForgotPassword,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    stringResource(R.string.action_forgot_password),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Button(
            onClick = { onSignIn(email, password) },
            enabled = email.isNotBlank() && password.isNotBlank() && !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.authButton),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.action_login), style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}

@Composable
private fun ErrorBox(message: String) {
    Surface(
        color = ErrorBackground,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = ErrorText,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null
) {
    val cdHidePassword = stringResource(R.string.cd_hide_password)
    val cdShowPassword = stringResource(R.string.cd_show_password)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (isPassword && onTogglePasswordVisibility != null) {
                {
                    IconButton(onClick = onTogglePasswordVisibility) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) cdHidePassword else cdShowPassword,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun ForgotPasswordDialog(
    uiState: AuthUiState,
    onSendReset: (email: String) -> Unit,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_reset_password)) },
        text = {
            if (uiState.passwordResetSent) {
                Text(stringResource(R.string.msg_reset_link_sent))
            } else {
                Column {
                    Text(stringResource(R.string.msg_reset_instructions))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text(stringResource(R.string.label_email)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (uiState.passwordResetSent) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
            } else {
                TextButton(
                    onClick = { onSendReset(email) },
                    enabled = email.isNotBlank() && !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.action_send_reset_email))
                    }
                }
            }
        },
        dismissButton = {
            if (!uiState.passwordResetSent) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}
