package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.rules.AuthInputValidator
import com.example.ui.components.ErrorBanner
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.viewmodel.AuthUiState

@Composable
fun LoginScreen(
    state: AuthUiState.SignedOut,
    onSignIn: (String, String) -> Unit,
    onResetPassword: (String) -> Unit,
    onSignUp: (email: String, password: String, confirmPassword: String, fullName: String?) -> Unit = { _, _, _, _ -> },
    onResendConfirmation: (String) -> Unit = {},
) {
    val colors = ApptividadesThemeDefaults.colors
    var isSignUpMode by rememberSaveable { mutableStateOf(false) }
    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var resetEmail by rememberSaveable { mutableStateOf("") }

    val strengthCheck = remember(password) { AuthInputValidator.checkPasswordStrength(password) }

    LaunchedEffect(state.signUpConfirmationSentCount) {
        if (state.signUpConfirmationSentCount > 0) {
            isSignUpMode = false
            password = ""
            confirmPassword = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_apptividades_emblem),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.ink,
                )
                Text(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            }

            // Segmented Auth Mode toggle: Iniciar sesión | Crear cuenta
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.neutralChipBg,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AuthModeTab(
                        label = stringResource(R.string.auth_tab_login),
                        selected = !isSignUpMode,
                        onClick = {
                            isSignUpMode = false
                            confirmPassword = ""
                        },
                        testTag = "auth_tab_login",
                        modifier = Modifier.weight(1f),
                    )
                    AuthModeTab(
                        label = stringResource(R.string.auth_tab_signup),
                        selected = isSignUpMode,
                        onClick = {
                            isSignUpMode = true
                            confirmPassword = ""
                        },
                        testTag = "auth_tab_signup",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (!state.isConfigured) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.accentSoft,
                    border = BorderStroke(1.dp, colors.accentBright),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.login_missing_config_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.accentHover,
                        )
                        Text(
                            text = stringResource(R.string.login_missing_config_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.ink,
                        )
                    }
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                ErrorBanner(message = state.errorMessage)
            }

            if (!state.unconfirmedEmail.isNullOrBlank()) {
                val resendEnabled = !state.isResendingConfirmation && state.resendCooldownSeconds <= 0
                OutlinedButton(
                    onClick = { onResendConfirmation(state.unconfirmedEmail) },
                    enabled = resendEnabled,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (resendEnabled) colors.accentBright else colors.line,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("login_resend_confirmation_button"),
                ) {
                    if (state.isResendingConfirmation) {
                        CircularProgressIndicator(
                            color = colors.accentHover,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(
                            text = if (state.resendCooldownSeconds > 0) {
                                stringResource(
                                    R.string.login_resend_confirmation_cooldown,
                                    state.resendCooldownSeconds,
                                )
                            } else {
                                stringResource(R.string.login_resend_confirmation)
                            },
                            color = if (resendEnabled) colors.accentHover else colors.muted,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            if (!state.infoMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.successBg,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.infoMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.successText,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (isSignUpMode) {
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text(stringResource(R.string.signup_name_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.ink,
                                unfocusedBorderColor = colors.line,
                                focusedLabelColor = colors.ink,
                                cursorColor = colors.accent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signup_name_input"),
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.login_email_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.ink,
                            unfocusedBorderColor = colors.line,
                            focusedLabelColor = colors.ink,
                            cursorColor = colors.accent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_email_input"),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.login_password_label)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = stringResource(
                                        if (passwordVisible) R.string.login_hide_password
                                        else R.string.login_show_password
                                    ),
                                    tint = colors.muted,
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (isSignUpMode) ImeAction.Next else ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (!isSignUpMode && !state.isSubmitting) onSignIn(email, password)
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.ink,
                            unfocusedBorderColor = colors.line,
                            focusedLabelColor = colors.ink,
                            cursorColor = colors.accent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_password_input"),
                    )

                    if (isSignUpMode) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text(stringResource(R.string.signup_confirm_password_label)) },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (!state.isSubmitting) {
                                        onSignUp(email, password, confirmPassword, fullName.ifBlank { null })
                                    }
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.ink,
                                unfocusedBorderColor = colors.line,
                                focusedLabelColor = colors.ink,
                                cursorColor = colors.accent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signup_confirm_password_input"),
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PasswordRuleRow(stringResource(R.string.signup_rule_length), strengthCheck.hasMinLength)
                            PasswordRuleRow(stringResource(R.string.signup_rule_letter), strengthCheck.hasLetter)
                            PasswordRuleRow(stringResource(R.string.signup_rule_digit), strengthCheck.hasDigit)
                            PasswordRuleRow(
                                stringResource(R.string.signup_rule_match),
                                confirmPassword.isNotEmpty() && password == confirmPassword,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            if (isSignUpMode) {
                                onSignUp(email, password, confirmPassword, fullName.ifBlank { null })
                            } else {
                                onSignIn(email, password)
                            }
                        },
                        enabled = !state.isSubmitting,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.ink,
                            contentColor = colors.surface,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag(if (isSignUpMode) "signup_submit_button" else "login_submit_button"),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                color = colors.surface,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text(
                                text = stringResource(
                                    if (isSignUpMode) R.string.signup_submit else R.string.login_submit
                                ),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }

                    if (!isSignUpMode) {
                        TextButton(
                            onClick = {
                                resetEmail = email
                                showResetDialog = true
                            },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .testTag("login_forgot_password_button"),
                        ) {
                            Text(
                                text = stringResource(R.string.login_forgot_password),
                                color = colors.accentHover,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = stringResource(R.string.login_reset_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.login_reset_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text(stringResource(R.string.login_email_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        onResetPassword(resetEmail)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ink,
                        contentColor = colors.surface,
                    ),
                ) {
                    Text(stringResource(R.string.login_reset_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.form_cancel), color = colors.muted)
                }
            },
        )
    }
}

@Composable
private fun AuthModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.surface else androidx.compose.ui.graphics.Color.Transparent,
        border = if (selected) BorderStroke(1.dp, colors.line) else null,
        modifier = modifier
            .height(44.dp)
            .testTag(testTag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                ),
                color = if (selected) colors.ink else colors.muted,
            )
        }
    }
}

@Composable
private fun PasswordRuleRow(label: String, satisfied: Boolean) {
    val colors = ApptividadesThemeDefaults.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (satisfied) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (satisfied) colors.success else colors.muted,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (satisfied) colors.successText else colors.muted,
        )
    }
}
