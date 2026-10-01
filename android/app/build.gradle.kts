import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.gstore.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gstore.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Configuração pública do cliente Neon (Auth + Data API).
        // Segredos NUNCA ficam aqui — a segurança é feita por RLS no
        // Postgres: cada token só lê/escreve o que as políticas permitem.
        // Estes valores vão dentro do APK de qualquer forma e podem ser
        // substituídos com -PNEON_AUTH_URL=... no build.
        buildConfigField("String", "NEON_AUTH_URL", "\"${project.findProperty("NEON_AUTH_URL") ?: ""}\"")
        buildConfigField("String", "NEON_DATA_API_URL", "\"${project.findProperty("NEON_DATA_API_URL") ?: ""}\"")
        buildConfigField("String", "NEON_AUTH_ORIGIN", "\"${project.findProperty("NEON_AUTH_ORIGIN") ?: "https://gstore.app"}\"")
    }

    // ── Assinatura de RELEASE ──────────────────────────────────────────
    // Lê as variáveis de ambiente (definidas no CI via GitHub Secrets, ou
    // manualmente numa máquina local):
    //   KEYSTORE_FILE      — caminho do keystore já decodificado (preferencial no CI)
    //   KEYSTORE_BASE64    — keystore em base64 (o Gradle decodifica para build/,
    //                        pasta ignorada pelo Git — conveniência local)
    //   KEYSTORE_PASSWORD  — senha do keystore
    //   KEY_ALIAS          — alias da chave
    //   KEY_PASSWORD       — senha da chave
    // NENHUM destes valores é versionado. Sem as variáveis, o APK de release
    // sai SEM assinatura (o CI verifica com apksigner e falha o build).
    signingConfigs {
        create("release") {
            val env = System.getenv()
            val keystoreFromB64: File? = env["KEYSTORE_BASE64"]
                ?.takeIf { it.isNotBlank() }
                ?.let { b64 ->
                    val decoded = Base64.getDecoder().decode(b64)
                    val destino = File(project.layout.buildDirectory.get().asFile, "keystore-release.decoded.jks")
                    destino.parentFile?.mkdirs()
                    destino.writeBytes(decoded)
                    destino
                }
            val keystoreFile: File? = env["KEYSTORE_FILE"]
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?: keystoreFromB64
            val envStorePassword = env["KEYSTORE_PASSWORD"]?.takeIf { it.isNotBlank() }
            val envKeyAlias = env["KEY_ALIAS"]?.takeIf { it.isNotBlank() }
            val envKeyPassword = env["KEY_PASSWORD"]?.takeIf { it.isNotBlank() }
            if (keystoreFile != null && envStorePassword != null && envKeyAlias != null && envKeyPassword != null) {
                storeFile = keystoreFile
                storePassword = envStorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // MINIFY/R8: DESLIGADO por segurança. O app usa Retrofit +
            // kotlinx.serialization, e código ofuscado só revela problemas
            // EM EXECUÇÃO (crashes ao fazer login/parsar JSON), que não dá
            // para testar no CI. Para ligar mais tarde:
            //   1) mudar os dois flags para true,
            //   2) gerar o APK, instalar no telemóvel e testar login,
            //      catálogo e download de jogos,
            //   3) só publicar se tudo funcionar.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            } else {
                logger.warn(
                    "AVISO: KEYSTORE_FILE/KEYSTORE_BASE64/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD " +
                        "não definidos — o APK de release NÃO será assinado (o CI falha nesta situação).",
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Rede: Retrofit + OkHttp + kotlinx.serialization
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Imagens
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Sessão local (DataStore)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
