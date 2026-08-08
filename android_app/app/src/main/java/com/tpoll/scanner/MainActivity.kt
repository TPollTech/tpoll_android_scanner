// Copyright (c) 2026 TPollTech. Todos os direitos reservados.
package com.tpoll.scanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.tpoll.scanner.ui.screens.CleanerScreen
import com.tpoll.scanner.ui.screens.DashboardScreen
import com.tpoll.scanner.ui.screens.HealthScreen
import com.tpoll.scanner.ui.screens.HistoryScreen
import com.tpoll.scanner.ui.screens.PermissionScreen
import com.tpoll.scanner.ui.screens.PremiumScreen
import com.tpoll.scanner.ui.screens.QuarantineScreen
import com.tpoll.scanner.ui.screens.SettingsScreen
import com.tpoll.scanner.ui.theme.AppGradients
import com.tpoll.scanner.ui.theme.LocalExtendedColors
import com.tpoll.scanner.ui.theme.TPollScannerTheme

class MainActivity : ComponentActivity() {

    private val currentUser = mutableStateOf<FirebaseUser?>(null)
    private val loginErrorMessage = mutableStateOf<String?>(null)
    private val loginSkipped = mutableStateOf(false)
    private var firebaseReady = false
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) checkAndStartScan()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            handleGoogleLoginResult(result.resultCode, result.data)
        }

        firebaseReady = hasFirebaseConfig()
        loginSkipped.value = getSharedPreferences("auth_settings", MODE_PRIVATE)
            .getBoolean("login_skipped", false)

        if (firebaseReady) currentUser.value = FirebaseAuth.getInstance().currentUser

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        requestRequiredPermissions()

        setContent {
            TPollScannerTheme {
                if (currentUser.value == null && !loginSkipped.value) {
                    LoginScreen(
                        errorMessage = loginErrorMessage.value,
                        onGoogleLogin = ::openGoogleLogin,
                        onContinueWithoutLogin = ::continueWithoutLogin
                    )
                } else {
                    MainScreen(onSignOut = ::signOut)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!firebaseReady) return

        FirebaseAuth.getInstance().currentUser?.let { user ->
            currentUser.value = user
            loginSkipped.value = false
            getSharedPreferences("auth_settings", MODE_PRIVATE)
                .edit()
                .putBoolean("login_skipped", false)
                .apply()
            loginErrorMessage.value = null
            checkAndStartScan()
        }
    }

    private fun hasFirebaseConfig(): Boolean {
        return try {
            FirebaseApp.getApps(this).isNotEmpty() || FirebaseApp.initializeApp(this) != null
        } catch (_: Exception) {
            false
        }
    }

    private fun openGoogleLogin() {
        if (!firebaseReady) {
            loginErrorMessage.value = "Login Google ainda não está configurado. Você pode continuar sem conta."
            return
        }

        loginErrorMessage.value = null
        try {
            val providers = arrayListOf(AuthUI.IdpConfig.GoogleBuilder().build())
            val signInIntent = AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .setTosAndPrivacyPolicyUrls(
                    "https://tpolltech.github.io/tpoll_android_scanner/privacy_policy.html",
                    "https://tpolltech.github.io/tpoll_android_scanner/privacy_policy.html"
                )
                .build()
            googleSignInLauncher.launch(signInIntent)
        } catch (e: Exception) {
            loginErrorMessage.value = "Não foi possível abrir o login Google: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

    private fun continueWithoutLogin() {
        loginSkipped.value = true
        loginErrorMessage.value = null
        getSharedPreferences("auth_settings", MODE_PRIVATE)
            .edit()
            .putBoolean("login_skipped", true)
            .apply()
        checkAndStartScan()
    }

    private fun handleGoogleLoginResult(resultCode: Int, data: Intent?) {
        if (!firebaseReady) return

        val response = IdpResponse.fromResultIntent(data)
        val user = FirebaseAuth.getInstance().currentUser
        currentUser.value = user

        if (resultCode == Activity.RESULT_OK && user != null) {
            loginSkipped.value = false
            getSharedPreferences("auth_settings", MODE_PRIVATE)
                .edit()
                .putBoolean("login_skipped", false)
                .apply()
            loginErrorMessage.value = null
            checkAndStartScan()
            return
        }

        loginErrorMessage.value = buildLoginErrorMessage(response)
    }

    private fun buildLoginErrorMessage(response: IdpResponse?): String {
        val error = response?.error
        if (response == null && error == null) return "Login cancelado. Tente novamente ou continue sem conta."
        if (error == null) return "Login não concluído. Tente novamente ou continue sem conta."
        val message = error.localizedMessage ?: error.message ?: "Erro desconhecido"
        return "Erro no login Google: $message"
    }

    private fun signOut() {
        loginSkipped.value = false
        getSharedPreferences("auth_settings", MODE_PRIVATE)
            .edit()
            .putBoolean("login_skipped", false)
            .apply()

        if (!firebaseReady) {
            currentUser.value = null
            return
        }

        AuthUI.getInstance().signOut(this).addOnCompleteListener {
            currentUser.value = null
            loginErrorMessage.value = null
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            checkAndStartScan()
        }
    }

    private fun checkAndStartScan() {
        if (firebaseReady && currentUser.value == null && !loginSkipped.value) return
        val prefs = getSharedPreferences("scan_settings", MODE_PRIVATE)
        if (prefs.getBoolean("auto_scan_enabled", true)) BootReceiver.schedulePeriodicScan(this)
    }
}

enum class Screen(
    val route: String,
    val label: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Dashboard("dashboard", "Início", "TPoll Scanner", Icons.Filled.Home, Icons.Outlined.Home),
    Cleaner("cleaner", "Limpeza", "Limpeza inteligente", Icons.Filled.CleaningServices, Icons.Outlined.CleaningServices),
    History("history", "Histórico", "Histórico", Icons.Filled.History, Icons.Outlined.History),
    Health("health", "Saúde", "Saúde do dispositivo", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    Premium("premium", "Premium", "TPoll Premium", Icons.Filled.Star, Icons.Outlined.Star),
    Settings("settings", "Ajustes", "Ajustes", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun LoginScreen(
    errorMessage: String?,
    onGoogleLogin: () -> Unit,
    onContinueWithoutLogin: () -> Unit
) {
    val extended = LocalExtendedColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(extended.gradientPrimary)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.2f),
                shadowElevation = 8.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(20.dp)
                        .size(48.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "TPoll Scanner",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Proteção automática contra malware",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onGoogleLogin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Entrar com Google",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onContinueWithoutLogin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Continuar sem conta",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = errorMessage,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(onSignOut: () -> Unit = {}) {
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }
    var showQuarantine by remember { mutableStateOf(false) }
    var showPermissions by remember { mutableStateOf(false) }

    if (showQuarantine) {
        QuarantineScreen(onBack = { showQuarantine = false })
        return
    }
    if (showPermissions) {
        PermissionScreen(onBack = { showPermissions = false })
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Screen.entries.forEach { screen ->
                        val selected = currentScreen == screen
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentScreen = screen },
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.label,
                                    modifier = Modifier.size(if (selected) 24.dp else 22.dp)
                                )
                            },
                            label = {
                                Text(
                                    screen.label,
                                    fontSize = if (selected) 11.sp else 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = currentScreen,
            modifier = Modifier.padding(paddingValues),
            transitionSpec = {
                fadeIn(tween(200)) + slideInVertically(tween(200), initialOffsetY = { it / 40 }) togetherWith
                    fadeOut(tween(150)) + slideOutVertically(tween(150), targetOffsetY = { it / 40 })
            },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                Screen.Dashboard -> DashboardScreen(
                    onNavigateToHistory = { currentScreen = Screen.History },
                    onNavigateToHealth = { currentScreen = Screen.Health },
                    onNavigateToQuarantine = { showQuarantine = true },
                    onNavigateToPermissions = { showPermissions = true }
                )
                Screen.Cleaner -> CleanerScreen()
                Screen.History -> HistoryScreen()
                Screen.Health -> HealthScreen()
                Screen.Premium -> PremiumScreen()
                Screen.Settings -> SettingsScreen(onSignOut = onSignOut)
            }
        }
    }
}
