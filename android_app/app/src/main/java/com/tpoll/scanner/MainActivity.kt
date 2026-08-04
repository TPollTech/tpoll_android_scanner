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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

enum class Screen(val route: String, val label: String, val title: String, val icon: ImageVector) {
    Dashboard("dashboard", "Início", "TPoll Scanner", Icons.Default.Home),
    Cleaner("cleaner", "Limpeza", "Limpeza inteligente", Icons.Default.CleaningServices),
    History("history", "Histórico", "Histórico", Icons.Default.History),
    Health("health", "Saúde", "Saúde do dispositivo", Icons.Default.Favorite),
    Premium("premium", "Premium", "TPoll Premium", Icons.Default.Star),
    Settings("settings", "Ajustes", "Ajustes", Icons.Default.Settings)
}

@Composable
fun LoginScreen(
    errorMessage: String?,
    onGoogleLogin: () -> Unit,
    onContinueWithoutLogin: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp).size(34.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "TPoll Scanner",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Antivírus, limpeza e privacidade para Android.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    Button(onClick = onGoogleLogin, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) {
                        Icon(painter = painterResource(id = R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Entrar com Google", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onContinueWithoutLogin,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Continuar sem conta")
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
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

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            TopAppBar(
                title = {
                    Text(currentScreen.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sair")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            ScrollableBottomBar(
                screens = Screen.entries,
                selected = currentScreen,
                onSelected = { currentScreen = it }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (currentScreen) {
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
                Screen.Settings -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun ScrollableBottomBar(
    screens: List<Screen>,
    selected: Screen,
    onSelected: (Screen) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(screens) { screen ->
                val isSelected = screen == selected
                Surface(
                    modifier = Modifier
                        .widthIn(min = 76.dp)
                        .clickable { onSelected(screen) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            screen.icon,
                            contentDescription = screen.label,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            screen.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
