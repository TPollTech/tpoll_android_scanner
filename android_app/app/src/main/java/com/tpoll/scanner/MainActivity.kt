// Copyright (c) 2026 TPollTech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPollTech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.tpoll.scanner.ui.screens.DashboardScreen
import com.tpoll.scanner.ui.screens.HealthScreen
import com.tpoll.scanner.ui.screens.HistoryScreen
import com.tpoll.scanner.ui.screens.PermissionScreen
import com.tpoll.scanner.ui.screens.QuarantineScreen
import com.tpoll.scanner.ui.screens.SettingsScreen
import com.tpoll.scanner.ui.theme.TPollScannerTheme

class MainActivity : ComponentActivity() {

    private val currentUser = mutableStateOf<FirebaseUser?>(null)
    private val loginErrorMessage = mutableStateOf<String?>(null)
    private var firebaseReady = false
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            checkAndStartScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            handleGoogleLoginResult(result.resultCode, result.data)
        }

        firebaseReady = hasFirebaseConfig()
        if (firebaseReady) {
            currentUser.value = FirebaseAuth.getInstance().currentUser
        }

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        requestRequiredPermissions()

        setContent {
            TPollScannerTheme {
                when {
                    !firebaseReady -> FirebaseSetupScreen()
                    currentUser.value == null -> LoginScreen(
                        errorMessage = loginErrorMessage.value,
                        onGoogleLogin = { openGoogleLogin() }
                    )
                    else -> MainScreen(
                        onSignOut = { signOut() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!firebaseReady) return

        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            currentUser.value = user
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
            loginErrorMessage.value = "Firebase ainda não está configurado. Confira se o google-services.json está em android_app/app/."
            return
        }

        loginErrorMessage.value = null

        try {
            val providers = arrayListOf(
                AuthUI.IdpConfig.GoogleBuilder().build()
            )

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

    private fun handleGoogleLoginResult(resultCode: Int, data: Intent?) {
        if (!firebaseReady) return

        val response = IdpResponse.fromResultIntent(data)
        val user = FirebaseAuth.getInstance().currentUser
        currentUser.value = user

        if (resultCode == Activity.RESULT_OK && user != null) {
            loginErrorMessage.value = null
            checkAndStartScan()
            return
        }

        loginErrorMessage.value = buildLoginErrorMessage(response)
    }

    private fun buildLoginErrorMessage(response: IdpResponse?): String {
        val error = response?.error

        if (response == null && error == null) {
            return "Login cancelado ou interrompido. Tente novamente. Se fechar de novo, confira o SHA-1/SHA-256 no Firebase."
        }

        if (error == null) {
            return "Login não concluído. Tente novamente."
        }

        val message = error.localizedMessage ?: error.message ?: "Erro desconhecido"
        return "Erro no login Google: $message\n\nConfira se o provedor Google está ativo no Firebase e se o SHA-1/SHA-256 deste APK foi cadastrado. Código: ${error.errorCode}"
    }

    private fun signOut() {
        if (!firebaseReady) {
            currentUser.value = null
            return
        }

        AuthUI.getInstance()
            .signOut(this)
            .addOnCompleteListener {
                currentUser.value = null
                loginErrorMessage.value = null
            }
    }

    private fun requestRequiredPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            checkAndStartScan()
        }
    }

    private fun checkAndStartScan() {
        if (!firebaseReady || currentUser.value == null) return

        val prefs = getSharedPreferences("scan_settings", MODE_PRIVATE)
        val autoScanEnabled = prefs.getBoolean("auto_scan_enabled", true)

        if (autoScanEnabled) {
            BootReceiver.schedulePeriodicScan(this)
        }
    }
}

enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    Dashboard("dashboard", "Painel", Icons.Default.Home),
    History("history", "Histórico", Icons.Default.History),
    Health("health", "Saúde", Icons.Default.Favorite),
    Settings("settings", "Configurações", Icons.Default.Settings)
}

@Composable
fun LoginScreen(
    errorMessage: String?,
    onGoogleLogin: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "TPoll Scanner",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Entre com sua conta Google para continuar usando o app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onGoogleLogin,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Entrar com Google")
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
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

@Composable
fun FirebaseSetupScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Configuração Firebase pendente",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Crie o projeto no Firebase, ative o login Google e coloque o arquivo google-services.json em android_app/app/ para liberar o acesso ao app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSignOut: () -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }
    var showQuarantine by remember { mutableStateOf(false) }
    var showPermissions by remember { mutableStateOf(false) }

    val screens = Screen.entries

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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 4.dp
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentScreen) {
                                Screen.Dashboard -> "TPoll Scanner"
                                Screen.History -> "Histórico"
                                Screen.Health -> "Saúde do dispositivo"
                                Screen.Settings -> "Configurações"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = onSignOut) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Sair"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            val isSelected = currentScreen.route == screen.route
                            if (isSelected) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Box(modifier = Modifier.padding(8.dp)) {
                                        Icon(screen.icon, contentDescription = screen.label, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            } else {
                                Icon(screen.icon, contentDescription = screen.label, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        },
                        label = {
                            Text(
                                screen.label,
                                fontWeight = if (currentScreen.route == screen.route) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        },
                        selected = currentScreen.route == screen.route,
                        onClick = { currentScreen = screen },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.padding(paddingValues)
        ) {
            when (currentScreen) {
                Screen.Dashboard -> DashboardScreen(
                    onNavigateToHistory = { currentScreen = Screen.History },
                    onNavigateToHealth = { currentScreen = Screen.Health },
                    onNavigateToQuarantine = { showQuarantine = true },
                    onNavigateToPermissions = { showPermissions = true }
                )
                Screen.History -> HistoryScreen()
                Screen.Health -> HealthScreen()
                Screen.Settings -> SettingsScreen()
            }
        }
    }
}
