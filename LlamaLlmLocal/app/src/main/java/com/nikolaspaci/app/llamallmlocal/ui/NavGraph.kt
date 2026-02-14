package com.nikolaspaci.app.llamallmlocal.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nikolaspaci.app.llamallmlocal.ui.chat.ChatScreen
import com.nikolaspaci.app.llamallmlocal.ui.common.HistoryMenuItems
import com.nikolaspaci.app.llamallmlocal.ui.home.HomeChatScreen
import com.nikolaspaci.app.llamallmlocal.ui.huggingface.HuggingFaceScreen
import com.nikolaspaci.app.llamallmlocal.ui.settings.SettingsScreen
import com.nikolaspaci.app.llamallmlocal.viewmodel.ChatViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.HistoryViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.HuggingFaceViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.ModelFileViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.ModelFileViewModelFactory
import com.nikolaspaci.app.llamallmlocal.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: Long): String {
            return "chat/$conversationId"
        }
    }
    object Settings : Screen("settings/{modelId}") {
        fun createRoute(modelId: String): String {
            return "settings/${Uri.encode(modelId)}"
        }
    }
    object HuggingFace : Screen("huggingface")
}

@Composable
fun AppNavigation(factory: ViewModelFactory) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val historyViewModel: HistoryViewModel = viewModel(factory = factory)
    val modelFileViewModel: ModelFileViewModel = viewModel(
        factory = ModelFileViewModelFactory(
            LocalContext.current,
            LocalContext.current.getSharedPreferences("app_prefs", 0)
        )
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column {
                    NavigationDrawerItem(
                        label = { Text("New Chat") },
                        selected = navController.currentDestination?.route == Screen.Home.route,
                        onClick = {
                            navController.navigate(Screen.Home.route)
                            scope.launch { drawerState.close() }
                        }
                    )
                    HorizontalDivider()
                    Text(
                        text = "Chats",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    HistoryMenuItems(
                        viewModel = historyViewModel,
                        onConversationClick = { conversationId ->
                            navController.navigate(Screen.Chat.createRoute(conversationId))
                            scope.launch { drawerState.close() }
                        },
                        onCloseMenu = {
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        }
    ) {
        NavHost(navController = navController, startDestination = Screen.Home.route) {
            composable(Screen.Home.route) {
                HomeChatScreen(
                    homeViewModel = viewModel(factory = factory),
                    modelFileViewModel = modelFileViewModel,
                    onStartChat = { conversationId ->
                        navController.navigate(Screen.Chat.createRoute(conversationId))
                    },
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    },
                    onNavigateToHuggingFace = {
                        navController.navigate(Screen.HuggingFace.route)
                    }
                )
            }
            composable(
                route = Screen.Chat.route,
                arguments = listOf(
                    navArgument("conversationId") { type = NavType.LongType }
                )
            ) {
                val chatViewModel: ChatViewModel = hiltViewModel()

                ChatScreen(
                    viewModel = chatViewModel,
                    modelFileViewModel = modelFileViewModel,
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    },
                    onNavigateToSettings = { modelId ->
                        navController.navigate(Screen.Settings.createRoute(modelId))
                    },
                    onNavigateToHuggingFace = {
                        navController.navigate(Screen.HuggingFace.route)
                    }
                )
            }
            composable(Screen.HuggingFace.route) {
                val huggingFaceViewModel: HuggingFaceViewModel = hiltViewModel()
                HuggingFaceScreen(
                    viewModel = huggingFaceViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onModelDownloaded = {
                        modelFileViewModel.loadCachedModels()
                        navController.popBackStack()
                    }
                )
            }
            composable(
                route = Screen.Settings.route,
                arguments = listOf(
                    navArgument("modelId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val modelId = backStackEntry.arguments?.getString("modelId") ?: ""
                SettingsScreen(
                    modelId = modelId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
