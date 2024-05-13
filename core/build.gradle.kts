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
        dependencies {
            implementation("com.sdercolin.utaformatix:utaformatix-data:1.0.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.6.4")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
            implementation(npm("jszip", "3.5.0"))
            implementation(npm("encoding-japanese", "1.0.30"))
            implementation(npm("uuid", "8.3.2"))
            implementation(npm("midi-parser-js", "4.0.4"))
            implementation(npm("js-yaml", "4.1.0"))
        }
    }
}

val cleanDistributedResources by tasks.register<Delete>("cleanDistributedResources") {
    listOf("format_templates", "images", "texts").forEach {
        delete("build/distributions/$it")
    }
    mustRunAfter("jsBrowserDistribution")
}
tasks.named("build") {
    dependsOn(cleanDistributedResources)
}
