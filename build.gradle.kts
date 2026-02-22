plugins {
    kotlin("jvm") version "1.9.0"
    application
}

group = "com.yome"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.yome.dozor.bootstrap.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}
