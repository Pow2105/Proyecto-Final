plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.ProyectoFinal" // Asegúrate de que el namespace coincida con el tuyo
    compileSdk = 35 // Puedes dejarlo en 35, no afecta la compatibilidad con minSdk 23.

    defaultConfig {
        applicationId = "com.example.ProyectoFinal" // Asegúrate de que el ID de la aplicación coincida con el tuyo
        minSdk = 23 // ¡Importante! Android 6.0 Marshmallow
        targetSdk = 35 // Target más alto para aprovechar APIs recientes sin romper compatibilidad.
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Agrega estas dependencias si necesitas usar JSON parsing (por ejemplo, con Gson)
    // Para el formato actual de string, no es estrictamente necesario Gson.
    // implementation("com.google.code.gson:gson:2.10.1")
}