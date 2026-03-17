package com.ycs.movietracker.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ycs.movietracker.R
import com.ycs.movietracker.ui.theme.AuthBackground
import com.ycs.movietracker.ui.theme.AuthButton
import com.ycs.movietracker.ui.theme.AuthInputField
import com.ycs.movietracker.ui.theme.MovietrackerTheme

@Composable
fun LoginScreen(
    onLoginClick: (email: String, password: String) -> Unit = { _, _ -> },
    onForgotPasswordClick: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Email
            Text(
                text = stringResource(R.string.label_email),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = AuthInputField,
                    focusedContainerColor = AuthInputField,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedTextColor = Color.Black,
                    focusedTextColor = Color.Black
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                shape = RoundedCornerShape(0.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password
            Text(
                text = stringResource(R.string.label_password),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = AuthInputField,
                    focusedContainerColor = AuthInputField,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedTextColor = Color.Black,
                    focusedTextColor = Color.Black
                ),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = RoundedCornerShape(0.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Login button
            Button(
                onClick = { onLoginClick(email, password) },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(185.dp)
                    .height(53.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AuthButton),
                shape = RoundedCornerShape(0.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_login),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Forgot Password
            TextButton(
                onClick = onForgotPasswordClick,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = stringResource(R.string.action_forgot_password),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 440, heightDp = 956)
@Composable
fun LoginScreenPreview() {
    MovietrackerTheme {
        LoginScreen()
    }
}
