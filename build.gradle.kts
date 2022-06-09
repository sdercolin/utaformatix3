plugins {
    kotlin("js") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"
    id("org.jlleitschuh.gradle.ktlint") version "9.4.1"
}

group = "com.sdercolin.utaformatix"

repositories {
    mavenCentral()
    jcenter()
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    version.set("0.36.0")
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib-js"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")

    // React, React DOM + Wrappers
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react:17.0.2-pre.290-kotlin-1.6.10")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom:17.0.2-pre.290-kotlin-1.6.10")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react-css:17.0.2-pre.290-kotlin-1.6.10")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-mui:5.8.0-pre.343")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-mui-icons:5.8.0-pre.343")

    // React components
    implementation(npm("react-file-drop", "3.1.2"))
    implementation(npm("react-is", "17.0.2"))
    implementation(npm("react-markdown", "5.0.3"))

    // Localization
    implementation(npm("i18next", "19.8.7"))
    implementation(npm("react-i18next", "11.8.5"))
    implementation(npm("i18next-browser-languagedetector", "6.0.1"))

    // Others
    implementation(npm("jszip", "3.5.0"))
    implementation(npm("file-saver", "2.0.5"))
    implementation(npm("raw-loader", "4.0.2"))
    implementation(npm("file-loader", "6.2.0"))
    implementation(npm("encoding-japanese", "1.0.30"))
    implementation(npm("uuid", "8.3.2"))
    implementation(npm("midi-parser-js", "4.0.4"))
    implementation(npm("js-cookie", "2.2.1"))
    implementation(npm("js-yaml", "4.1.0"))

    // Testing
    testImplementation(kotlin("test"))
}

kotlin {
    js(LEGACY) {
        browser {
            binaries.executable()
            commonWebpackConfig {
                cssSupport.enabled = true
            }
        }
    }
}

tasks.register("stage") {
    dependsOn("build")
}
