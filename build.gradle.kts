plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.4.1"
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

tasks {
    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        relocate("com.fasterxml.jackson", "org.OAuth_Framework.oAuth_Framework.libs.jackson")
        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }
}
