plugins {
    kotlin("js")
    kotlin("plugin.serialization")
}

group = "com.sdercolin.utaformatix"

repositories {
    mavenCentral()
}

kotlin {
    js(IR) {
        binaries.library()
        browser()
        generateTypeScriptDefinitions()
        useEsModules()
        dependencies {
            implementation("com.sdercolin.utaformatix:utaformatix-data:1.0.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.6.4")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
            implementation(npm("jszip", "3.5.0"))
            implementation(npm("encoding-japanese", "1.0.30"))
            implementation(npm("uuid", "8.3.2"))
            implementation(npm("midi-file", "1.2.4"))
            implementation(npm("js-yaml", "4.1.0"))
            implementation(
                npm("@sevenc-nanashi/valuetree-ts", "npm:@jsr/sevenc-nanashi__valuetree-ts@0.2.1"),
            )
        }
    }
}
