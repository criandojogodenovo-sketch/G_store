package com.gstore.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gstore.app.data.remote.GameDto
import com.gstore.app.data.repo.DownloadRepository
import com.gstore.app.screens.ConfigErrorScreen
import com.gstore.app.screens.auth.AuthViewModel
import com.gstore.app.screens.auth.LoginScreen
import com.gstore.app.screens.auth.RegisterScreen
import com.gstore.app.screens.categories.CategoriesScreen
import com.gstore.app.screens.categories.CategoriesViewModel
import com.gstore.app.screens.developer.DashboardScreen
import com.gstore.app.screens.developer.DeveloperViewModel
import com.gstore.app.screens.developer.EditGameScreen
import com.gstore.app.screens.developer.GameVersionsScreen
import com.gstore.app.screens.developer.PublishGameScreen
import com.gstore.app.screens.gamedetails.GameDetailsScreen
import com.gstore.app.screens.gamedetails.GameDetailsViewModel
import com.gstore.app.screens.home.HomeScreen
import com.gstore.app.screens.home.HomeViewModel
import com.gstore.app.screens.library.LibraryScreen
import com.gstore.app.screens.library.LibraryViewModel
import com.gstore.app.screens.profile.ProfileScreen
import com.gstore.app.screens.profile.ProfileViewModel
import com.gstore.app.screens.search.SearchScreen
import com.gstore.app.screens.search.SearchViewModel
import com.gstore.app.ui.theme.GStoreTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GStoreApp
        setContent {
            GStoreTheme {
                // Erro CLARO no arranque se a configuração pública do
                // Appwrite estiver em falta (endpoint/project id).
                val missingConfig = remember { ConfigCheck.fromBuild() }
                if (missingConfig.isNotEmpty()) {
                    ConfigErrorScreen(missingConfig)
                } else {
                    GStoreNavHost(app)
                }
            }
        }
    }
}

private data class BottomNavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun GStoreNavHost(app: GStoreApp) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val downloads = DownloadRepository(context)

    // Splash leve (2s) — valida serviços em background.
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(2000); showSplash = false }

    if (showSplash) {
        com.gstore.app.screens.splash.SplashScreen()
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val bottomItems = listOf(
        BottomNavItem("home", "Início", Icons.Filled.Home),
        BottomNavItem("search", "Buscar", Icons.Filled.Search),
        BottomNavItem("categories", "Categorias", Icons.Filled.Widgets),
        BottomNavItem("library", "Biblioteca", Icons.Filled.Download),
        BottomNavItem("profile", "Perfil", Icons.Filled.Person),
    )
    val showBottomBar = currentRoute in bottomItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                val vm: HomeViewModel = viewModel(factory = viewModelFactory { HomeViewModel(app.repository) })
                HomeScreen(
                    viewModel = vm,
                    onOpenGame = { navController.navigate("game/${it.slug}") },
                    onOpenSearch = { navController.navigate("search") },
                    onOpenCategory = { navController.navigate("category/$it") },
                )
            }
            composable("search") {
                val vm: SearchViewModel = viewModel(factory = viewModelFactory { SearchViewModel(app.repository) })
                SearchScreen(
                    viewModel = vm,
                    onOpenGame = { navController.navigate("game/${it.slug}") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("categories") {
                val vm: CategoriesViewModel = viewModel(factory = viewModelFactory { CategoriesViewModel(app.repository) })
                CategoriesScreen(
                    viewModel = vm,
                    onOpenCategory = { navController.navigate("category/$it") },
                )
            }
            composable("category/{slug}") { entry ->
                val slug = entry.arguments?.getString("slug") ?: return@composable
                val vm: SearchViewModel = viewModel(key = "cat-$slug", factory = viewModelFactory { SearchViewModel(app.repository) })
                LaunchedEffect(slug) {
                    vm.onQueryChange("") // reset
                    vm.searchCategory(slug)
                }
                SearchScreen(
                    viewModel = vm,
                    onOpenGame = { navController.navigate("game/${it.slug}") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "game/{slug}",
                arguments = listOf(navArgument("slug") { type = NavType.StringType }),
            ) { entry ->
                val slug = entry.arguments?.getString("slug") ?: return@composable
                val vm: GameDetailsViewModel = viewModel(
                    key = "game-$slug",
                    factory = GameDetailsViewModel.factory(app.repository, downloads, slug),
                )
                GameDetailsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("library") {
                val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(app.repository))
                LibraryScreen(viewModel = vm, appContext = context)
            }
            composable("login") {
                val vm: AuthViewModel = viewModel(factory = AuthViewModel.factory(app.repository))
                LoginScreen(
                    viewModel = vm,
                    onAuthenticated = { navController.popBackStack() },
                    onGoRegister = { navController.navigate("register") },
                )
            }
            composable("register") {
                val vm: AuthViewModel = viewModel(factory = AuthViewModel.factory(app.repository))
                RegisterScreen(
                    viewModel = vm,
                    onAuthenticated = { navController.popBackStack() },
                    onGoLogin = { navController.popBackStack() },
                )
            }
            composable("profile") {
                val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(app.repository, app.sessionStore))
                ProfileScreen(
                    viewModel = vm,
                    onOpenDeveloperDashboard = { navController.navigate("developer") },
                    onOpenLogin = { navController.navigate("login") },
                )
            }
            composable("developer") {
                val vm: DeveloperViewModel = viewModel(factory = DeveloperViewModel.factory(app.repository))
                DashboardScreen(
                    viewModel = vm,
                    onOpenVersions = { id, name -> navController.navigate("versions/$id?name=$name") },
                    onOpenEdit = { navController.navigate("edit/$it") },
                    onOpenPublish = { navController.navigate("publish") },
                )
            }
            composable("publish") {
                val vm: DeveloperViewModel = viewModel(factory = DeveloperViewModel.factory(app.repository))
                PublishGameScreen(
                    viewModel = vm,
                    onPublished = { navController.popBackStack() },
                )
            }
            composable(
                route = "edit/{gameId}",
                arguments = listOf(navArgument("gameId") { type = NavType.StringType }),
            ) { entry ->
                val gameId = entry.arguments?.getString("gameId") ?: return@composable
                val vm: DeveloperViewModel = viewModel(factory = DeveloperViewModel.factory(app.repository))
                EditGameScreen(
                    gameId = gameId,
                    viewModel = vm,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "versions/{gameId}?name={name}",
                arguments = listOf(
                    navArgument("gameId") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val gameId = entry.arguments?.getString("gameId") ?: return@composable
                val name = entry.arguments?.getString("name") ?: ""
                val vm: DeveloperViewModel = viewModel(factory = DeveloperViewModel.factory(app.repository))
                GameVersionsScreen(
                    gameId = gameId,
                    gameName = name,
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/** Fábrica simples de ViewModels sem bibliotecas extras. */
private inline fun <reified T : androidx.lifecycle.ViewModel> viewModelFactory(
    crossinline create: () -> T,
): androidx.lifecycle.ViewModelProvider.Factory = object : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <V : androidx.lifecycle.ViewModel> create(modelClass: Class<V>): V = create() as V
}
