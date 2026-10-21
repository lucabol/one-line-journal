plugins {
    id("com.android.application") version "8.10.1" apply false
    kotlin("android") version "2.1.20" apply false
    kotlin("jvm") version "2.1.20" apply false
    kotlin("plugin.serialization") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("com.google.devtools.ksp") version "2.1.20-2.0.1" apply false
}

tasks.wrapper {
    distributionSha256Sum = "f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6"
}
