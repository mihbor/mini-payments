
plugins {
  kotlin("multiplatform") version "1.6.10"
  kotlin("plugin.serialization") version "1.6.10"
  id("org.jetbrains.compose") version "1.0.1"
}

repositories {
  mavenCentral()
  maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
  google()
  flatDir(mapOf("dir" to "libs"))
}

kotlin {
  js(IR) {
    browser()
    binaries.executable()
  }
  sourceSets {
    val jsMain by getting {
      dependencies {
        implementation(compose.web.core)
        implementation(compose.runtime)
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")
        
        implementation("com.ionspin.kotlin:bignum:0.3.3")
        implementation("com.ionspin.kotlin:bignum-serialization-kotlinx:0.3.3")
  
        implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.klib"))))

        
        implementation(npm("qrcode", "1.5.0"))
        implementation(npm("qr-scanner", "1.3.0"))
      }
    }
  }
}

tasks.register<Zip>("minidappDistribution") {
  dependsOn("jsBrowserDistribution")
  archiveFileName.set("miniPayments.minidapp")
  destinationDirectory.set(layout.buildDirectory.dir("minidapp"))
  from(layout.buildDirectory.dir("distributions")) {
    exclude("*.map")
  }
}