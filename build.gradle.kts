
val firebaseSdkVersion = "1.6.2"

plugins {
  kotlin("multiplatform") version "1.7.20"
  kotlin("plugin.serialization") version "1.7.20"
  id("org.jetbrains.compose") version "1.2.1"
}

repositories {
  mavenCentral()
  maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
  google()
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
    
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
        
        implementation("com.ionspin.kotlin:bignum:0.3.7")
        implementation("com.ionspin.kotlin:bignum-serialization-kotlinx:0.3.7")
  
        implementation("dev.gitlive:firebase-firestore-js:$firebaseSdkVersion")
        
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
