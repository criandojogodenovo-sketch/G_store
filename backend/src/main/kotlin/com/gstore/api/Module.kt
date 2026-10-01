package com.gstore.api

import com.gstore.api.auth.AuthSupport
import com.gstore.api.config.AppConfig
import com.gstore.api.db.Database
import com.gstore.api.db.Migrator
import com.gstore.api.repositories.CategoryRepository
import com.gstore.api.repositories.DownloadRepository
import com.gstore.api.repositories.GameRepository
import com.gstore.api.repositories.UserRepository
import com.gstore.api.services.AppwriteService
import com.gstore.api.services.GitHubService
import com.gstore.api.services.PublishService

/**
 * Módulo de dependências da API (injeção manual — simples e explícito).
 */
class AppModule(config: AppConfig) {
    val config: AppConfig = config
    val database = Database(config.databaseUrl)
    val gameRepository = GameRepository(database)
    val categoryRepository = CategoryRepository(database)
    val userRepository = UserRepository(database)
    val downloadRepository = DownloadRepository(database)
    val appwriteService = AppwriteService(
        endpoint = config.appwriteEndpoint,
        projectId = config.appwriteProjectId,
        apiKey = config.appwriteApiKey,
    )
    val githubService = GitHubService(
        token = config.githubToken,
        repository = config.githubReleasesRepo,
    )
    val publishService = PublishService(
        games = gameRepository,
        categories = categoryRepository,
        users = userRepository,
        github = githubService,
    )
    val authSupport = AuthSupport(
        appwrite = appwriteService,
        users = userRepository,
        adminEmails = config.adminEmails,
        authDisabled = config.authDisabled,
    )

    init {
        // Migrações idempotentes aplicadas no startup (seguras contra reexecução).
        Migrator(database).migrate()
    }

    fun close() {
        database.close()
    }
}
