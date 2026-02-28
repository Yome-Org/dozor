import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("com.diffplug.spotless") version "6.25.0"
  kotlin("jvm") version "1.9.0"
  kotlin("plugin.serialization") version "1.9.0"
  application
}

group = "com.yome"

version = "0.1.0-SNAPSHOT"

repositories { mavenCentral() }

dependencies {
  implementation(kotlin("stdlib"))
  implementation("org.postgresql:postgresql:42.7.7")
  implementation("org.flywaydb:flyway-core:10.20.1")
  implementation("org.flywaydb:flyway-database-postgresql:10.20.1")
  implementation("redis.clients:jedis:5.2.0")
  implementation("org.yaml:snakeyaml:2.2")
  implementation("io.ktor:ktor-server-core-jvm:2.3.13")
  implementation("io.ktor:ktor-server-netty-jvm:2.3.13")
  implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.13")
  implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.13")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

  testImplementation(kotlin("test"))
  testImplementation("org.testcontainers:junit-jupiter:1.21.3")
  testImplementation("org.testcontainers:postgresql:1.21.3")
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

application { mainClass.set("com.yome.dozor.bootstrap.ApplicationKt") }

spotless {
  kotlin {
    target("src/**/*.kt")
    ktfmt().googleStyle()
  }

  kotlinGradle {
    target("*.gradle.kts")
    ktfmt().googleStyle()
  }
}

tasks.test { useJUnitPlatform() }
