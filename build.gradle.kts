plugins {
    application
}

group = "dev.atomixsoft"
version = "1.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("com.formdev:flatlaf:3.7.2")
}

application {
    mainClass = "dev.atomixsoft.randomizer.App"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}