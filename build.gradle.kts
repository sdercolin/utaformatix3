plugins {
    kotlin("multiplatform") version "1.8.22"
    kotlin("plugin.serialization") version "1.8.22"
    id("org.jlleitschuh.gradle.ktlint") version "10.3.0"
}

group = "com.sdercolin.utaformatix"

repositories {
    mavenCentral()
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("0.45.2")
        enableExperimentalRules.set(true)
    }
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
                implementation(project(":core"))

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
                implementation(npm("stream-browserify", "3.0.0"))
                implementation(npm("buffer", "6.0.3"))
                implementation(npm("file-saver", "2.0.5"))
                implementation(npm("raw-loader", "4.0.2"))
                implementation(npm("file-loader", "6.2.0"))
                implementation(npm("js-cookie", "2.2.1"))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

val copyCoreResources by tasks.register<Copy>("copyCoreResources") {
    from("core/src/main/resources") {
        include("**/*.*")
    }
    into("build/js/packages/utaformatix/kotlin/")
    mustRunAfter("jsProductionExecutableCompileSync")
    mustRunAfter("jsDevelopmentExecutableCompileSync")
}
tasks.named("jsBrowserProductionWebpack") {
    dependsOn(copyCoreResources)
}
tasks.named("jsBrowserDevelopmentRun") {
    dependsOn(copyCoreResources)
}

val copyJsResourcesForTests by tasks.register<Copy>("copyJsResourcesForTests") {
    from("core/src/main/resources") {
        include("**/*.*")
    }
    from("src/jsMain/resources") {
        include("**/*.*")
    }
    into("build/js/packages/utaformatix-test/kotlin")
    mustRunAfter("jsTestTestDevelopmentExecutableCompileSync")
}
tasks.named("jsBrowserTest") {
    dependsOn(copyJsResourcesForTests)
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
