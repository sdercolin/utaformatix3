plugins {
    kotlin("js")
}

group = "com.sdercolin.utaformatix"

repositories {
    mavenCentral()
}

kotlin {
    js {
        binaries.library()
        browser {}
    }
}
