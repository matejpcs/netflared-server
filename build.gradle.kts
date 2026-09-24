plugins {
    java
}

group = "com.matejpcs.netflared"
version = providers.gradleProperty("pluginVersion").orElse("0.1.2-SNAPSHOT").get()

description = "Easy Cloudflare Tunnel management for Paper Minecraft servers"

val paperVersion = providers.gradleProperty("paperVersion").orElse("26.2.build.+").get()
val javaVersion = providers.gradleProperty("javaVersion").orElse("25").get().toInt()
val apiVersion = providers.gradleProperty("apiVersion").orElse("26.2").get()
val artifactVersion = providers.gradleProperty("artifactVersion").orElse(paperVersion).get()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    compileOnly("com.google.code.gson:gson:2.13.2")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand(
            "version" to project.version,
            "apiVersion" to apiVersion
        )
    }
}

tasks.jar {
    archiveFileName.set("netflared-server-$artifactVersion.jar")
}
