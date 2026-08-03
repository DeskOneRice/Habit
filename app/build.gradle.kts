import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val habitLocalProperties = Properties().apply {
    rootProject.file("local.properties").inputStream().use(::load)
}
val habitSigningStoreFile = requireNotNull(habitLocalProperties.getProperty("habit.signing.storeFile")) {
    "Missing habit.signing.storeFile in local.properties"
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.habit.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.habit.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 8
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("habitStable") {
            storeFile = file(habitSigningStoreFile)
            storePassword = requireNotNull(habitLocalProperties.getProperty("habit.signing.storePassword"))
            keyAlias = requireNotNull(habitLocalProperties.getProperty("habit.signing.keyAlias"))
            keyPassword = requireNotNull(habitLocalProperties.getProperty("habit.signing.keyPassword"))
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("habitStable")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("habitStable")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.documentfile)
    implementation(libs.kotlinx.serialization.json)

    ksp(libs.room.compiler)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
