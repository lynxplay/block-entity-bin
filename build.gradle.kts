plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.5.15"
    id("xyz.jpenilla.run-paper") version "2.2.3"
}

java.toolchain.languageVersion = JavaLanguageVersion.of(17)
tasks.withType<JavaCompile> { options.encoding = Charsets.UTF_8.name() }
tasks.assemble { dependsOn(tasks.reobfJar) }

val devBundleVersion = properties["paperweightDevBundleVersion"]?.toString() ?: "1.20.4-R0.1-SNAPSHOT"
val minecraftVersion = devBundleVersion.split("-")[0]

version = "${properties["version"].toString()}+${minecraftVersion}"

dependencies {
    paperweight.paperDevBundle(devBundleVersion)
}

tasks {
    runServer {
        minecraftVersion(minecraftVersion)
    }
}
