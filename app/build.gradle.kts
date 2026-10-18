
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.woode.vpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.woode.vpn"
        minSdk = 23
        targetSdk = 35

        // رقم البناء التالي بعد الإصدار السابق
        versionCode = 2

        // اسم الإصدار الجديد
        versionName = "3.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // محرك اتصال WireGuard الحالي
    implementation("com.wireguard.android:tunnel:1.0.20250531")

    // عرض واجهة التطبيق المحلية
    implementation("androidx.webkit:webkit:1.14.0")

    coreLibraryDesugaring(
        "com.android.tools:desugar_jdk_libs:2.1.5"
    )
}
