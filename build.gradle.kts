plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.4.1"
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    // 编译基线锁定 1.16.5：用最低目标版本的 Spigot API 编译，强制只调用 1.16.5 即有的 API 子集，
    // 编译期即防止误用 1.17+ 新增 API，从而保证单一 shadowJar 可在 1.16.5 ~ 1.21.x 全版本运行。
    // （Java 17 toolchain 编译 1.16.5 的 Java 8 bytecode API 依赖完全兼容。）
    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.10.0")
    testImplementation("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
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
        relocate("com.fasterxml.jackson", "org.linuxdo.oauthlink.libs.jackson")
        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }
}
