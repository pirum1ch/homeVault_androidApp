package com.homevalt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.homevalt.app.data.database.HomeVaultDatabase
import com.homevalt.app.data.network.JwtInterceptor
import com.homevalt.app.data.network.NetworkMonitor
import com.homevalt.app.data.preferences.EncryptedPrefs
import com.homevalt.app.data.repository.AuthRepository
import com.homevalt.app.data.repository.FileRepository
import com.homevalt.app.data.repository.NetworkSwitcher
import com.homevalt.app.ui.screens.FileDetailScreen
import com.homevalt.app.ui.screens.FileListScreen
import com.homevalt.app.ui.screens.LoginScreen
import com.homevalt.app.ui.screens.ProfileScreen
import com.homevalt.app.ui.screens.UploadQueueScreen
import com.homevalt.app.viewmodel.FileDetailViewModel
import com.homevalt.app.viewmodel.FileListViewModel
import com.homevalt.app.viewmodel.LoginViewModel
import com.homevalt.app.viewmodel.ProfileViewModel
import com.homevalt.app.viewmodel.UploadQueueViewModel

class MainActivity : ComponentActivity() {

    private lateinit var encryptedPrefs: EncryptedPrefs
    private lateinit var jwtInterceptor: JwtInterceptor
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var networkSwitcher: NetworkSwitcher
    private lateinit var authRepository: AuthRepository
    private lateinit var fileRepository: FileRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = HomeVaultDatabase.getDatabase(applicationContext)
        encryptedPrefs = EncryptedPrefs(applicationContext)
        jwtInterceptor = JwtInterceptor(encryptedPrefs)
        networkMonitor = NetworkMonitor(applicationContext)
        networkSwitcher = NetworkSwitcher(encryptedPrefs, networkMonitor, applicationContext)
        authRepository = AuthRepository(encryptedPrefs, networkSwitcher, jwtInterceptor, db, applicationContext)
        fileRepository = FileRepository(encryptedPrefs, networkSwitcher, jwtInterceptor, db, applicationContext)

        val loginViewModel = ViewModelProvider(this, vmFactory { LoginViewModel(application, authRepository, encryptedPrefs) })[LoginViewModel::class.java]
        val fileListViewModel = ViewModelProvider(this, vmFactory { FileListViewModel(application, fileRepository, authRepository) })[FileListViewModel::class.java]
        val fileDetailViewModel = ViewModelProvider(this, vmFactory { FileDetailViewModel(application, fileRepository) })[FileDetailViewModel::class.java]
        val profileViewModel = ViewModelProvider(this, vmFactory { ProfileViewModel(application, authRepository, encryptedPrefs) })[ProfileViewModel::class.java]
        val uploadQueueViewModel = ViewModelProvider(this, vmFactory { UploadQueueViewModel(application, fileRepository) })[UploadQueueViewModel::class.java]

        val startDestination = when {
            authRepository.isLoggedIn() && authRepository.isBiometricEnabled() -> "login"
            authRepository.isLoggedIn() -> "file_list"
            else -> "login"
        }

        setContent {
            HomeVaultTheme {
                Surface {
                    val navController = rememberNavController()

                    LaunchedEffect(Unit) {
                        JwtInterceptor.sessionExpiredFlow.collect {
                            navController.navigate("login") { popUpTo(0) { inclusive = true } }
                        }
                    }

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("login") { LoginScreen(navController, loginViewModel) }
                        composable("file_list") { FileListScreen(navController, fileListViewModel) }
                        composable(
                            route = "file_detail/{fileId}",
                            arguments = listOf(navArgument("fileId") { type = NavType.StringType })
                        ) { back ->
                            FileDetailScreen(navController, back.arguments?.getString("fileId") ?: "", fileDetailViewModel)
                        }
                        composable("profile") { ProfileScreen(navController, profileViewModel) }
                        composable("upload_queue") { UploadQueueScreen(navController, uploadQueueViewModel) }
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : ViewModel> vmFactory(create: () -> T) = object : ViewModelProvider.Factory {
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    }
}

private val HvColorScheme = darkColorScheme(
    background = Color(0xFF0D0D0D),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFCCCCCC),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF6B6B6B),
    primary = Color(0xFFB3F542),
    onPrimary = Color(0xFF0D1A02),
    primaryContainer = Color(0xFF1A2A06),
    onPrimaryContainer = Color(0xFFB3F542),
    secondary = Color(0xFF22C55E),
    onSecondary = Color(0xFF081A0E),
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF2D0808),
    onErrorContainer = Color(0xFFEF4444),
    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF1E1E1E),
    scrim = Color(0xCC000000),
)

private val HvTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
)

@Composable
private fun HomeVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HvColorScheme, typography = HvTypography, content = content)
}
