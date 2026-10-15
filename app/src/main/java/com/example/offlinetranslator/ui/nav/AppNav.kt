package com.example.offlinetranslator.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.offlinetranslator.ui.screen.conversation.ConversationScreen
import com.example.offlinetranslator.ui.screen.conversation.ConversationViewModel
import com.example.offlinetranslator.ui.screen.image.ImageTranslateScreen
import com.example.offlinetranslator.ui.screen.image.ImageTranslateViewModel
import com.example.offlinetranslator.ui.screen.models.ModelManageScreen
import com.example.offlinetranslator.ui.screen.models.ModelManageViewModel
import com.example.offlinetranslator.ui.screen.settings.SettingsScreen
import com.example.offlinetranslator.ui.screen.settings.SettingsViewModel
import com.example.offlinetranslator.ui.screen.translate.TranslateScreen
import com.example.offlinetranslator.ui.screen.translate.TranslateViewModel

sealed class TopDest(val route: String, val label: String, val icon: ImageVector) {
    data object Translate : TopDest("translate", "翻译", Icons.Default.Translate)
    data object Conversation : TopDest("conversation", "对话", Icons.Default.SwapHoriz)
    data object Image : TopDest("image", "图片", Icons.Default.Image)
    data object Models : TopDest("models", "模型", Icons.Default.Folder)
    data object Settings : TopDest("settings", "设置", Icons.Default.Settings)
}

@Composable
fun AppNav(
    translateVm: TranslateViewModel,
    conversationVm: ConversationViewModel,
    imageVm: ImageTranslateViewModel,
    modelVm: ModelManageViewModel,
    settingsVm: SettingsViewModel,
) {
    val navController = rememberNavController()
    val items = listOf(TopDest.Translate, TopDest.Conversation, TopDest.Image, TopDest.Models, TopDest.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                items.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = TopDest.Translate.route,
            modifier = Modifier.padding(inner),
        ) {
            composable(TopDest.Translate.route) {
                TranslateScreen(
                    vm = translateVm,
                    onOpenImage = { navController.navigate(TopDest.Image.route) },
                    onOpenConversation = { navController.navigate(TopDest.Conversation.route) },
                )
            }
            composable(TopDest.Conversation.route) {
                ConversationScreen(vm = conversationVm)
            }
            composable(TopDest.Image.route) {
                ImageTranslateScreen(vm = imageVm)
            }
            composable(TopDest.Models.route) {
                ModelManageScreen(vm = modelVm)
            }
            composable(TopDest.Settings.route) {
                SettingsScreen(vm = settingsVm)
            }
        }
    }
}
