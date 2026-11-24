plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("telegramBuildPlugin") {
            id = "org.telegram.build-plugin"
            implementationClass = "org.telegram.plugin.TelegramBuildPlugin"
        }
        register("telegramBuildAppPlugin") {
            id = "org.telegram.build-app-plugin"
            implementationClass = "org.telegram.plugin.TelegramBuildAppPlugin"
        }
    }
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
    implementation("com.android.tools.build:gradle:9.1.1")
    implementation("com.android.tools:sdk-common:32.1.1")
    implementation("com.google.code.gson:gson:2.15.0")
}
