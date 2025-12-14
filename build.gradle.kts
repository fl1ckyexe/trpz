plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

javafx {
    version = "21"
    modules = listOf(
        "javafx.controls",
        "javafx.graphics",
        "javafx.base"
    )
}

dependencies {
    // SQLite
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")

    // Logging (щоб не було warning'ів)
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("org.slf4j:slf4j-simple:2.0.13")

    // Tests
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    // ВАЖЛИВО: JavaFX Application class
    mainClass.set("org.example.ui.DownloadApp")
}

tasks.test {
    useJUnitPlatform()
}
