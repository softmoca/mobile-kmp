package kr.jachwisunbae.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kr.jachwisunbae.app.data.ApiClient
import kr.jachwisunbae.app.platform.rememberPlatformServices
import kr.jachwisunbae.app.platform.rememberTokenStore
import kr.jachwisunbae.app.ui.App

@Composable
fun JachwiApp(baseUrl: String) {
    val tokenStore = rememberTokenStore()
    val platform = rememberPlatformServices()
    val client = remember {
        HttpClient {
            expectSuccess = false
            followRedirects = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
            }
        }
    }
    val api = remember(client, tokenStore, baseUrl) { ApiClient(client, tokenStore, baseUrl) }
    DisposableEffect(client) { onDispose { client.close() } }
    App(api, platform)
}
