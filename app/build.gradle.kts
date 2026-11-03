import java.util.Properties

// Chave de envio da Play Store em keystore.properties (fora do git), mesmo padrão do Caronas.
// Sem ela o release sai com a chave de debug: instala para teste, mas não serve para a Play Store.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val temKeystoreDeRelease = keystorePropertiesFile.exists()
if (temKeystoreDeRelease) {
    keystoreProperties.load(keystorePropertiesFile.inputStream().reader(Charsets.UTF_8))
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.cjstudio.condominio_sociedade_morro_grande"
    // SDK 36: a Play Store não aceita app novo com alvo mais antigo
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cjstudio.condominio_sociedade_morro_grande"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (temKeystoreDeRelease) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (temKeystoreDeRelease) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    // Política de Privacidade dentro do app (tela Configurações), copiada do painel-web: um texto só
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/politica"))

    flavorDimensions += "version"

    productFlavors {
        create("admin") {
            dimension = "version"
            applicationIdSuffix = ".admin"
            versionNameSuffix = "-admin"
            resValue("string", "app_name", "Admins")
            buildConfigField("String", "PERFIL", "\"admin\"")
        }
        create("morador") {
            dimension = "version"
            applicationIdSuffix = ".morador"
            versionNameSuffix = "-morador"
            resValue("string", "app_name", "Moradores")
            buildConfigField("String", "PERFIL", "\"morador\"")
        }
    }
}

val copiarPoliticaPrivacidade by tasks.registering(Copy::class) {
    from(rootProject.file("painel-web")) { include("privacidade.html", "icone.png", "favicon.png") }
    into(layout.buildDirectory.dir("generated/politica"))
}
tasks.named("preBuild") { dependsOn(copiarPoliticaPrivacidade) }

dependencies {
    // Android Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.fragment:fragment-ktx:1.6.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Image Loading
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-functions")
    // Notificação de mensagem nova no chat (CondominioMessagingService)
    implementation("com.google.firebase:firebase-messaging")

    // Assinatura mensal pelo Google Play (sem Mercado Pago) — ver GooglePlayBillingManager.java
    implementation("com.android.billingclient:billing:8.0.0")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
