import com.android.build.api.variant.BuildConfigField
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Facebook App ID for the Instagram Stories intent (`source_application`).
// Resolved from local.properties, then a Gradle property, then an environment variable.
val facebookAppId: Provider<String> = providers
    .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
    .asText
    .map { Properties().apply { load(it.reader()) }.getProperty("facebook.appId").orEmpty().trim() }
    .filter { it.isNotEmpty() }
    .orElse(providers.gradleProperty("facebook.appId"))
    .orElse(providers.environmentVariable("FACEBOOK_APP_ID"))
    .orElse("")

android {
    namespace = "app.storymaker"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.storymaker"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.buildConfigFields?.put(
            "FACEBOOK_APP_ID",
            facebookAppId.map { BuildConfigField("String", "\"$it\"", "Facebook App ID for Instagram Stories sharing") },
        )
    }
}

// Release builds must not ship without a Facebook App ID; debug builds may leave it empty.
val checkFacebookAppId = tasks.register("checkFacebookAppId") {
    val appId = facebookAppId
    inputs.property("facebookAppId", appId)
    doLast {
        if (appId.get().isBlank()) {
            throw GradleException(
                "Missing Facebook App ID. Set facebook.appId in local.properties, " +
                    "pass -Pfacebook.appId=..., or set the FACEBOOK_APP_ID environment variable.",
            )
        }
    }
}
tasks.named { it == "preReleaseBuild" }.configureEach { dependsOn(checkFacebookAppId) }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
