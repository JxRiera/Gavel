plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
}

group = "dev.jxriera"
version = "1.4.1"

val javaRelease = 8

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://repo.helpch.at/releases/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("litebans:api:0.6.1")
    compileOnly("me.clip:placeholderapi:2.11.6")

    // Spigot and Paper ship the SQLite and MySQL drivers, so the server's copy is used.
    // MySQL is loaded by name because Spigot 1.16 and 1.17 ship Connector/J 5.1.
    compileOnly("org.xerial:sqlite-jdbc:3.45.3.0")

    implementation("org.bstats:bstats-bukkit:3.1.0")
    implementation("org.postgresql:postgresql:42.7.4")

    testImplementation("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaRelease)
    options.compilerArgs.add("-Xlint:-options")
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()

    relocate("org.bstats", "dev.jxriera.gavel.lib.bstats")
    relocate("org.postgresql", "dev.jxriera.gavel.lib.postgresql")

    from(rootProject.file("LICENSE")) { into("META-INF/gavel") }
    from(rootProject.file("NOTICE")) { into("META-INF/gavel") }

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("org/checkerframework/**")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
