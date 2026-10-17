plugins {
    `java-library`
}

group = "com.beer"
version = "0.1.0"

// Paper 26.3 only (Java 25).

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.175-stable")
    testImplementation("io.papermc.paper:paper-api:26.3.build.175-stable")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

// The resource pack ships inside the jar; reproducible so its sha1 only changes when the pack does.
val packZip by tasks.registering(Zip::class) {
    from("resourcepack")
    archiveFileName.set("beer-pack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("pack"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
        from(packZip)
    }
    test {
        useJUnitPlatform()
    }
    withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
        options.release.set(25)
    }
}
