plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Lee google-services.json y genera la configuración de Firebase.
    alias(libs.plugins.google.services)
}

android {
    namespace = "ar.edu.utn.frsfco.finanzas"
    compileSdk = 34

    defaultConfig {
        applicationId = "ar.edu.utn.frsfco.finanzas"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.recyclerview)
    implementation(libs.coroutines.play.services)

    // Firebase. La lista de materiales evita declarar la versión de cada biblioteca.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // Analytics no se invoca desde el código: alcanza con incluirlo para que
    // registre aperturas y sesiones, que es la parte de "medir" del trabajo.
    implementation(libs.firebase.analytics)

    // Entrada con Google
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.auth)
    implementation(libs.googleid)
}
