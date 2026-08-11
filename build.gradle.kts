plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
}

group = "dev.jxriera"
version = "1.0.1"

val javaRelease = 8

val excludedNatives = listOf(
    "Linux-Android", "FreeBSD",
    "Linux/ppc64", "Linux/x86", "Linux/arm", "Linux/armv6", "Linux/armv7",
    "Linux-Musl/x86",
    "Windows/x86", "Windows/armv7"
)

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")

    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("com.mysql:mysql-connector-j:8.4.0") {
        exclude(group = "com.google.protobuf")
    }
    implementation("org.postgresql:postgresql:42.7.4")

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

    relocate("org.sqlite", "dev.jxriera.gavel.lib.sqlite")
    relocate("com.mysql", "dev.jxriera.gavel.lib.mysql")
    relocate("org.postgresql", "dev.jxriera.gavel.lib.postgresql")

    from(rootProject.file("LICENSE")) { into("META-INF/gavel") }
    from(rootProject.file("NOTICE")) { into("META-INF/gavel") }

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("org/checkerframework/**")
    for (platform in excludedNatives) {
        exclude("org/sqlite/native/$platform/**")
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
