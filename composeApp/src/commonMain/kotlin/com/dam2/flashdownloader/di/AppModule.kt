package com.dam2.flashdownloader.di

import com.dam2.flashdownloader.data.manager.DownloadManagerImpl
import com.dam2.flashdownloader.data.network.DownloadClient
import com.dam2.flashdownloader.data.repository.DownloadRepositoryImpl
import com.dam2.flashdownloader.domain.manager.DownloadManager
import com.dam2.flashdownloader.domain.repository.DownloadRepository
import com.dam2.flashdownloader.presentation.viewmodel.DownloadViewModel
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Módulo común de Koin con las dependencias compartidas
 */
fun commonModule() = module {
    // HTTP Client
    single {
        HttpClient(CIO) {
            engine {
                maxConnectionsCount = 1000
                endpoint {
                    maxConnectionsPerRoute = 100
                    pipelineMaxSize = 20  // ✅ Reducido de 50 para estabilidad
                    keepAliveTime = 600_000  // ✅ 10 minutos (mantener conexión viva)
                    connectTimeout = 180_000  // ✅ 3 minutos para establecer conexión
                    connectAttempts = 10  // ✅ Aumentado de 5 a 10
                    socketTimeout = 900_000  // ✅ 15 minutos sin recibir datos
                }
                // ✅ Configuración adicional para archivos muy grandes
                https {
                    trustManager = null  // Usar el trust manager del sistema
                }
            }

            // ✅ CRÍTICO: Seguir redirecciones HTTP automáticamente
            install(HttpRedirect) {
                checkHttpMethod = false  // Permitir redirecciones en cualquier método HTTP
                allowHttpsDowngrade = false  // No permitir downgrade de HTTPS a HTTP
            }

            // ✅ CRÍTICO: Configurar timeouts HTTP para archivos grandes (2GB+)
            install(HttpTimeout) {
                connectTimeoutMillis = 180_000  // ✅ 3 minutos para conectar
                socketTimeoutMillis = 900_000   // ✅ 15 minutos entre bytes
                requestTimeoutMillis = null     // ✅ SIN LÍMITE total para descargas largas
            }
        }
    }

    // DownloadClient
    single { DownloadClient(get()) }

    // Repository (la implementación específica de plataforma se provee en módulos separados)
    single<DownloadRepository> { DownloadRepositoryImpl(get()) }

    // CoroutineScope para el DownloadManager
    single {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    // DownloadManager (los parámetros específicos de plataforma se inyectan)
    single<DownloadManager> {
        DownloadManagerImpl(
            downloadClient = get(),
            repository = get(),
            fileWriterFactory = get(),
            downloadPath = get(named("downloadPath")),
            scope = get()
        )
    }

    // ViewModel
    single {
        DownloadViewModel(
            downloadManager = get(),
            clipboardManager = get()
        )
    }
}

/**
 * Función expect para obtener el módulo específico de plataforma
 */
expect fun platformModule(): Module
