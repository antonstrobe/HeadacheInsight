package com.neuron.headacheinsight.app

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neuron.headacheinsight.core.designsystem.LocalHandPreference
import com.neuron.headacheinsight.feature.attachments.AttachmentsRoute
import com.neuron.headacheinsight.feature.episode.EpisodeRoute
import com.neuron.headacheinsight.feature.home.HomeRoute
import com.neuron.headacheinsight.feature.insights.InsightsRoute
import com.neuron.headacheinsight.feature.onboarding.OnboardingRoute
import com.neuron.headacheinsight.feature.profile.ProfileRoute
import com.neuron.headacheinsight.feature.questionnaire.QuestionnaireRoute
import com.neuron.headacheinsight.feature.quicklog.QuickLogRoute
import com.neuron.headacheinsight.feature.reports.ReportsRoute
import com.neuron.headacheinsight.feature.settings.SettingsRoute
import com.neuron.headacheinsight.feature.sync.SyncRoute

sealed class HeadacheInsightDestination(val route: String) {
    data object Language : HeadacheInsightDestination("language")
    data object Onboarding : HeadacheInsightDestination("onboarding")
    data object Home : HeadacheInsightDestination("home")
    data object QuickLog : HeadacheInsightDestination("quicklog")
    data object Profile : HeadacheInsightDestination("profile")
    data object Attachments : HeadacheInsightDestination("attachments")
    data object Reports : HeadacheInsightDestination("reports")
    data object Settings : HeadacheInsightDestination("settings")
    data object Sync : HeadacheInsightDestination("sync")
    data object Insights : HeadacheInsightDestination("insights")
    data object Episode : HeadacheInsightDestination("episode/{episodeId}") {
        fun create(episodeId: String) = "episode/$episodeId"
    }
    data object Questionnaire : HeadacheInsightDestination("questionnaire/{episodeId}") {
        fun create(episodeId: String) = "questionnaire/$episodeId"
    }
}

@Composable
fun HeadacheInsightNavHost(
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val state by appViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    fun resolveStartRoute(): String = when {
        !state.settings.languageSelectionCompleted -> HeadacheInsightDestination.Language.route
        state.settings.onboardingCompleted || state.hasProfile -> HeadacheInsightDestination.Home.route
        else -> HeadacheInsightDestination.Onboarding.route
    }

    LaunchedEffect(state.settings.languageTag) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(state.settings.languageTag),
        )
    }

    LaunchedEffect(state.settings.onboardingCompleted, state.hasProfile, currentRoute) {
        val startRoute = resolveStartRoute()
        if (currentRoute == null) {
            navController.navigate(startRoute)
        }
    }

    fun relaunchApp() {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        } ?: return
        context.startActivity(launchIntent)
        (context as? Activity)?.finish()
    }

    fun navigateHome() {
        navController.navigate(HeadacheInsightDestination.Home.route) {
            popUpTo(HeadacheInsightDestination.Home.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    CompositionLocalProvider(LocalHandPreference provides state.settings.handPreference) {
        NavHost(
            navController = navController,
            startDestination = resolveStartRoute(),
        ) {
            composable(HeadacheInsightDestination.Language.route) {
                LanguageSelectionScreen(
                    onSelectRussian = {
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags("ru-RU"),
                        )
                        appViewModel.selectLanguage("ru-RU") {
                            relaunchApp()
                        }
                    },
                    onSelectEnglish = {
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags("en-US"),
                        )
                        appViewModel.selectLanguage("en-US") {
                            relaunchApp()
                        }
                    },
                )
            }
            composable(HeadacheInsightDestination.Onboarding.route) {
                OnboardingRoute(
                    onComplete = {
                        navController.navigate(HeadacheInsightDestination.Home.route) {
                            popUpTo(HeadacheInsightDestination.Onboarding.route) { inclusive = true }
                        }
                    },
                )
            }
            composable(HeadacheInsightDestination.Home.route) {
                HomeRoute(
                    onStartEpisode = { navController.navigate(HeadacheInsightDestination.QuickLog.route) },
                    onContinueEpisode = { navController.navigate(HeadacheInsightDestination.Questionnaire.create(it)) },
                    onHistory = { navController.navigate(HeadacheInsightDestination.Insights.route) },
                    onProfile = { navController.navigate(HeadacheInsightDestination.Profile.route) },
                    onAttachments = { navController.navigate(HeadacheInsightDestination.Attachments.route) },
                    onReports = { navController.navigate(HeadacheInsightDestination.Reports.route) },
                    onQuestions = { navController.navigate(HeadacheInsightDestination.Questionnaire.create(it)) },
                    onSettings = { navController.navigate(HeadacheInsightDestination.Settings.route) },
                    onSync = { navController.navigate(HeadacheInsightDestination.Sync.route) },
                )
            }
            composable(HeadacheInsightDestination.QuickLog.route) {
                QuickLogRoute(
                    onOpenEpisode = { navController.navigate(HeadacheInsightDestination.Episode.create(it)) },
                    onBack = { navController.popBackStack() },
                    onHome = ::navigateHome,
                )
            }
            composable(
                route = HeadacheInsightDestination.Episode.route,
                arguments = listOf(navArgument("episodeId") { type = NavType.StringType }),
            ) {
                EpisodeRoute(
                    onBack = { navController.popBackStack() },
                    onHome = ::navigateHome,
                    onOpenQuestions = { navController.navigate(HeadacheInsightDestination.Questionnaire.create(it)) },
                )
            }
            composable(
                route = HeadacheInsightDestination.Questionnaire.route,
                arguments = listOf(navArgument("episodeId") { type = NavType.StringType }),
            ) {
                QuestionnaireRoute(
                    onBack = { navController.popBackStack() },
                    onHome = ::navigateHome,
                )
            }
            composable(HeadacheInsightDestination.Profile.route) {
                ProfileRoute(
                    onBack = { navController.popBackStack() },
                    onHome = ::navigateHome,
                )
            }
            composable(HeadacheInsightDestination.Attachments.route) {
                AttachmentsRoute(
                    onBack = { navController.popBackStack() },
                    onHome = ::navigateHome,
                )
            }
            composable(HeadacheInsightDestination.Insights.route) {
                InsightsRoute(
                    onBack = { navController.popBackStack() },
                    onHome = ::navigateHome,
                    onOpenEpisode = { navController.navigate(HeadacheInsightDestination.Episode.create(it)) },
                )
            }
            composable(HeadacheInsightDestination.Reports.route) {
                ReportsRoute(
                    onBack = { navController.popBackStack() },
                    onHome = ::navigateHome,
                )
            }
            composable(HeadacheInsightDestination.Settings.route) {
                SettingsRoute(
                    onBack = { navController.popBackStack() },
                    onHome = ::navigateHome,
                )
            }
            composable(HeadacheInsightDestination.Sync.route) {
                SyncRoute(
                    onBack = { navController.popBackStack() },
                    onHome = ::navigateHome,
                )
            }
        }
    }
}
