plugins {
    kotlin("multiplatform") version "1.8.22"
    kotlin("plugin.serialization") version "1.8.22"
    id("org.jlleitschuh.gradle.ktlint") version "10.3.0"
}

group = "com.sdercolin.utaformatix"

repositories {
    mavenCentral()
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    version.set("0.45.2")
    enableExperimentalRules.set(true)
}

fun kotlinw(target: String): String =
    "org.jetbrains.kotlin-wrappers:kotlin-$target"

kotlin {
    js {
        binaries.executable()
        browser {
            commonWebpackConfig {
                devServer = devServer?.copy(port = 33221)
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }
    sourceSets {
        val jsMain by getting {
            dependencies {
                // Model
                implementation("com.sdercolin.utaformatix:utaformatix-data:1.0.0")

                // Kotlin
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.6.4")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")

                // React, React DOM + Wrappers
                implementation(enforcedPlatform("org.jetbrains.kotlin-wrappers:kotlin-wrappers-bom:1.0.0-pre.430"))
                implementation(kotlinw("emotion"))
                implementation(kotlinw("extensions"))
                implementation(kotlinw("react"))
                implementation(kotlinw("react-dom"))
                implementation(kotlinw("mui"))
                implementation(kotlinw("mui-icons"))

                // React components
                implementation(npm("react-file-drop", "3.1.2"))
                implementation(npm("react-markdown", "5.0.3"))

                // Localization
                implementation(npm("i18next", "19.8.7"))
                implementation(npm("react-i18next", "11.8.5"))
                implementation(npm("i18next-browser-languagedetector", "6.0.1"))

                // Others
                implementation(npm("jszip", "3.5.0"))
                implementation(npm("stream-browserify", "3.0.0"))
                implementation(npm("buffer", "6.0.3"))
                implementation(npm("file-saver", "2.0.5"))
                implementation(npm("raw-loader", "4.0.2"))
                implementation(npm("file-loader", "6.2.0"))
                implementation(npm("encoding-japanese", "1.0.30"))
                implementation(npm("uuid", "8.3.2"))
                implementation(npm("midi-parser-js", "4.0.4"))
                implementation(npm("js-cookie", "2.2.1"))
                implementation(npm("js-yaml", "4.1.0"))
            }
        }
        val copyJsResources by tasks.register<Copy>("copyJsResources") {
            from("src/jsMain/resources")
            into("build/js/packages/utaformatix-test/kotlin")
        }
        tasks.named("jsBrowserTest") {
            dependsOn(copyJsResources)
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
