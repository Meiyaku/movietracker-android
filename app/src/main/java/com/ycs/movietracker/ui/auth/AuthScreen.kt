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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                text = "Movie Tracker",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Your personal movie collection",
                fontSize = 13.sp,
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        listOf("Log In", "Sign Up").forEachIndexed { index, label ->
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
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 14.sp
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

    val localError = if (confirmPassword.isNotEmpty() && password != confirmPassword) {
        "Passwords do not match"
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
            label = "Email",
            value = email,
            onValueChange = { email = it; onFieldChange() },
            keyboardType = KeyboardType.Email
        )
        AuthField(
            label = "Password",
            value = password,
            onValueChange = { password = it; onFieldChange() },
            keyboardType = KeyboardType.Password,
            isPassword = true,
            passwordVisible = showPassword,
            onTogglePasswordVisibility = { showPassword = !showPassword }
        )
        AuthField(
            label = "Confirm Password",
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
                Text("Sign Up", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
            label = "Email",
            value = email,
            onValueChange = { email = it; onFieldChange() },
            keyboardType = KeyboardType.Email
        )
        AuthField(
            label = "Password",
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
                    "Forgot Password?",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
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
                Text("Log In", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ErrorBox(message: String) {
    Surface(
        color = Color(0xFFFFEBEE),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            color = Color(0xFFB71C1C),
            fontSize = 13.sp,
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
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
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
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
        title = { Text("Reset Password") },
        text = {
            if (uiState.passwordResetSent) {
                Text("If an account exists for that email, a reset link has been sent.")
            } else {
                Column {
                    Text("Enter your email address and we'll send you a reset link.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text("Email") },
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
                TextButton(onClick = onDismiss) { Text("OK") }
            } else {
                TextButton(
                    onClick = { onSendReset(email) },
                    enabled = email.isNotBlank() && !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Send Reset Email")
                    }
                }
            }
        },
        dismissButton = {
            if (!uiState.passwordResetSent) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
