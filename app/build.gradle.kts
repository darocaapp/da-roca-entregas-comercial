plugins {
    id("com.android.application")
}

android {
    namespace = "com.daroca.entregas"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.daroca.entregas"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    // Leitura de texto das comandas
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Carregamento seguro do HTML no Android
    implementation("androidx.webkit:webkit:1.12.1")
}
