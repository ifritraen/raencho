plugins {
    id("java-library")
    id("maven-publish")
    alias(libs.plugins.gradle.shadow)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    compileOnly(libs.echo.common)
    compileOnly(libs.kotlin.stdlib)
    implementation(libs.jsoup)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.echo.common)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

// Extension properties goto `gradle.properties` to set values

val extType: String by project
val extId: String by project
val extClass: String by project

val extIconUrl: String? by project
val extName: String by project
val extDescription: String? by project

val extAuthor: String by project
val extAuthorUrl: String? by project

val extRepoUrl: String? by project
val extUpdateUrl: String? by project

val gitHash = execute("git", "rev-parse", "HEAD").take(7)
val gitCount = execute("git", "rev-list", "--count", "HEAD").toInt()
val verCode = gitCount
val verName = "v$gitHash"

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "dev.brahmkshatriya.echo.extension"
            artifactId = extId
            version = verName

            from(components["java"])
        }
    }
}

tasks {
    shadowJar {
        archiveBaseName.set(extId)
        archiveVersion.set(verName)
        manifest {
            attributes(
                mapOf(
                    "Extension-Id" to extId,
                    "Extension-Type" to extType,
                    "Extension-Class" to extClass,

                    "Extension-Version-Code" to verCode,
                    "Extension-Version-Name" to verName,

                    "Extension-Icon-Url" to extIconUrl,
                    "Extension-Name" to extName,
                    "Extension-Description" to extDescription,

                    "Extension-Author" to extAuthor,
                    "Extension-Author-Url" to extAuthorUrl,

                    "Extension-Repo-Url" to extRepoUrl,
                    "Extension-Update-Url" to extUpdateUrl
                )
            )
        }
    }
}

// Multi-extension configurations for raencode
data class ExtConfig(
    val id: String,
    val className: String,
    val name: String,
    val description: String,
    val iconUrl: String
)

val extensionsList = listOf(
    ExtConfig(
        id = "audiochan",
        className = "dev.brahmkshatriya.echo.extension.Audiochan",
        name = "Audiochan",
        description = "Audiochan extension for Echo audio player.",
        iconUrl = "https://audiochan.com/pwa/apple-touch-icon.v2.png"
    ),
    ExtConfig(
        id = "audiolove",
        className = "dev.brahmkshatriya.echo.extension.Audiolove",
        name = "Audio.love",
        description = "Audio.love extension for Echo audio player.",
        iconUrl = "https://audio.love/favicon.ico"
    ),
    ExtConfig(
        id = "hotaudio",
        className = "dev.brahmkshatriya.echo.extension.Hotaudio",
        name = "HotAudio.net",
        description = "HotAudio.net extension for Echo audio player.",
        iconUrl = "https://hotaudio.net/icon.svg"
    )
)

extensionsList.forEach { ext ->
    tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar_${ext.id}") {
        group = "shadow"
        description = "Create a shadow JAR for extension ${ext.name}"
        
        archiveBaseName.set(ext.id)
        archiveClassifier.set("")
        archiveVersion.set(verName)
        
        from(sourceSets.main.get().output)
        configurations = listOf(project.configurations.runtimeClasspath.get())
        
        manifest {
            attributes(
                mapOf(
                    "Extension-Id" to ext.id,
                    "Extension-Type" to "music",
                    "Extension-Class" to ext.className,
                    "Extension-Version-Code" to verCode,
                    "Extension-Version-Name" to verName,
                    "Extension-Icon-Url" to ext.iconUrl,
                    "Extension-Name" to ext.name,
                    "Extension-Description" to ext.description,
                    "Extension-Author" to "raencode",
                    "Extension-Author-Url" to "https://github.com/itsmechinmoy/echo-extensions",
                    "Extension-Repo-Url" to "",
                    "Extension-Update-Url" to "https://raw.githubusercontent.com/itsmechinmoy/echo-extensions/main/echo_extensions.json"
                )
            )
        }
    }
}

val buildAllExtensions by tasks.registering {
    group = "build"
    description = "Build shadow JARs for all registered extensions"
    dependsOn(extensionsList.map { "shadowJar_${it.id}" })
}

fun execute(vararg command: String): String = providers.exec {
    commandLine(*command)
}.standardOutput.asText.get().trim()