plugins {
    id("java")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    // SQLite JDBC
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    // SLF4J
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    // JavaFX
    implementation("org.openjfx:javafx-controls:21")
    implementation("org.openjfx:javafx-fxml:21")
}

tasks.test {
    useJUnitPlatform()
}