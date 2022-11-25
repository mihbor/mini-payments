val ktorVersion = "2.1.2"
val bignumVersion = "0.3.7"
val firebaseSdkVersion = "1.6.2"

plugins {
  kotlin("multiplatform") version "1.7.20"
  kotlin("plugin.serialization") version "1.7.20"
  id("org.jetbrains.compose") version "1.2.1"
}

repositories {
  google()
  mavenCentral()
  maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
  maven("https://maven.pkg.github.com/mihbor/MinimaK") {
    credentials {
      username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
      password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
    }
  }
}

kotlin {
  js(IR) {
    browser{
      commonWebpackConfig {
        sourceMaps = true
      }
    }
    binaries.executable()
  }
  sourceSets {
    val jsMain by getting {
      dependencies {
        implementation(compose.web.core)
        implementation(compose.runtime)
        implementation("io.ktor:ktor-client-core:$ktorVersion")
        implementation("io.ktor:ktor-client-js:$ktorVersion")

        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")

        implementation("com.ionspin.kotlin:bignum:$bignumVersion")
        implementation("com.ionspin.kotlin:bignum-serialization-kotlinx:$bignumVersion")
  
        implementation("dev.gitlive:firebase-firestore-js:$firebaseSdkVersion")

        implementation("ltd.mbor:minimak:0.1-SNAPSHOT")

        implementation(npm("qrcode", "1.5.0"))
        implementation(npm("qr-scanner", "1.3.0"))
      }
    }
  }
}

tasks.register<Zip>("minidappDistribution") {
  dependsOn("jsBrowserDistribution")
  archiveFileName.set("${project.name}.mds.zip")
  destinationDirectory.set(layout.buildDirectory.dir("minidapp"))
  from(layout.buildDirectory.dir("distributions"))
}

configurations.all {
  resolutionStrategy.cacheChangingModulesFor(1, "minutes")
}
