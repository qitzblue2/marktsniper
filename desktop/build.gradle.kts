import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    sourceSets["main"].kotlin.srcDir("../shared/src")
}

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
}

compose.desktop {
    application {
        mainClass = "nl.marktsniper.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "MarktSniper"
            packageVersion = "1.0.0"
            description = "Koopjes-meldingen voor Marktplaats, 2dehands en Vinted"
            modules("java.net.http", "java.desktop", "java.logging", "jdk.crypto.ec")
            windows {
                menu = true
                shortcut = true
                perUserInstall = true
                iconFile.set(project.file("icon.ico"))
                upgradeUuid = "6f1c1f0e-6a55-4c4e-9a0b-6d9b8d1c2a11"
            }
            macOS { iconFile.set(project.file("icon.icns")) }
            linux { iconFile.set(project.file("icon.png")) }
        }
        buildTypes.release.proguard { isEnabled.set(false) }
    }
}
