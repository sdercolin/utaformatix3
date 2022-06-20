plugins {
    kotlin("js") version "1.4.30"
    kotlin("plugin.serialization") version "1.4.30-RC"
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.4.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")

    // React, React DOM + Wrappers
    implementation("org.jetbrains:kotlin-react:17.0.1-pre.148-kotlin-1.4.21")
    implementation("org.jetbrains:kotlin-react-dom:17.0.1-pre.148-kotlin-1.4.21")
    implementation("org.jetbrains:kotlin-styled:5.2.1-pre.148-kotlin-1.4.21")
    implementation(npm("react", "17.0.2"))
    implementation(npm("react-dom", "17.0.2"))

    // React components
    implementation(npm("@material-ui/core", "4.11.4"))
    implementation(npm("@material-ui/icons", "4.11.2"))
    implementation(npm("@material-ui/lab", "4.0.0-alpha.58"))
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
    testImplementation(kotlin("test-js"))
}

kotlin {
    js(LEGACY) {
        browser {
            binaries.executable()
            webpackTask {
                cssSupport.enabled = true
            }
            runTask {
                cssSupport.enabled = true
            }
            testTask {
                useKarma {
                    enabled = System.getenv("GRADLE_TEST_DISABLED") != "true"
                    useChromeHeadless()
                    webpackConfig.cssSupport.enabled = true
                }
            }
        }
    }
}

tasks.register("stage") {
    dependsOn("build")
}
