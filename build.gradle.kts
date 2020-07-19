plugins {
    id("org.jetbrains.kotlin.js") version "1.3.71"
    kotlin("plugin.serialization") version "1.3.71"
}

group = "com.sdercolin.utaformatix"

repositories {
    maven("https://kotlin.bintray.com/kotlin-js-wrappers/")
    mavenCentral()
    jcenter()
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib-js"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-js:0.20.0")

    // React, React DOM + Wrappers
    implementation("org.jetbrains:kotlin-react:16.13.0-pre.94-kotlin-1.3.70")
    implementation("org.jetbrains:kotlin-react-dom:16.13.0-pre.94-kotlin-1.3.70")
    implementation(npm("react", "16.13.1"))
    implementation(npm("react-dom", "16.13.1"))
    implementation("org.jetbrains:kotlin-styled:1.0.0-pre.94-kotlin-1.3.70")
    implementation(npm("styled-components"))
    implementation(npm("inline-style-prefixer"))

    // React components
    implementation(npm("@material-ui/core"))
    implementation(npm("@material-ui/icons"))
    implementation(npm("@material-ui/lab", "4.0.0-alpha.56"))
    implementation(npm("react-file-drop"))
    implementation(npm("react-is"))

    // Localization
    implementation(npm("i18next"))
    implementation(npm("react-i18next"))
    implementation(npm("i18next-browser-languagedetector"))

    // Others
    implementation(npm("jszip"))
    implementation(npm("file-saver"))
    implementation(npm("raw-loader"))
    implementation(npm("file-loader"))
    implementation(npm("encoding-japanese"))
    implementation(npm("uuid"))

    // Testing
    testImplementation(kotlin("test-js"))
}

kotlin.target {
    browser {
        testTask {
            useKarma {
                useChromeHeadless()
                useFirefox()
            }
        }
    }
}

tasks.register("stage") {
    dependsOn("build")
}
