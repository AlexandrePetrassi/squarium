plugins {
    kotlin("js") version "1.4.21"
}
kotlin {
    js {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
            commonWebpackConfig {
                cssSupport.enabled = true
                cssSupport.mode = "import"
            }
            binaries.executable()
        }
    }
}
repositories {
    mavenCentral()
}
dependencies {
    testImplementation(kotlin("test-js"))
    implementation(kotlin("stdlib-js"))
    implementation(npm("uuid","latest"))
}
