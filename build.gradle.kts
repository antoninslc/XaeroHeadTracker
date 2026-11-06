plugins {
    id("dev.kikugie.loom-back-compat")
    id("maven-publish")
}

class Versions(
    val fabricApi: String,
    val worldMap: String,
    val carpet: String,
    val java: Int,
    val loader: String,
)

val mc: String = stonecutter.current.version
val v = mapOf(
    "26.3" to Versions("0.163.0+26.3", "1.47.0", "26.3", 25, "0.19.5"),
    "26.2" to Versions("0.153.0+26.2", "1.41.3", "26.2", 25, "0.19.3"),
    "1.21.11" to Versions("0.141.6+1.21.11", "1.47.0", "1.4.194", 21, "0.19.3"),
    "1.21.1" to Versions("0.116.12+1.21.1", "1.47.0", "1.4.147", 21, "0.19.3"),
).getValue(mc)

version = property("mod_version") as String
group = property("maven_group") as String

repositories {
    maven("https://chocolateminecraft.com/maven") { name = "Xaero's Maven" }
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content { includeGroup("maven.modrinth") }
    }
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("xaeroheadtracker") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }

    runs {
        named("client") {
            programArgs("--quickPlaySingleplayer", "Test ${mc.replace('.', '_')}")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${v.loader}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${v.fabricApi}")
    modImplementation("xaero.map:xaeroworldmap-fabric-$mc:${v.worldMap}")
    if (mc == "26.2") {
        // World Map 1.41.3 embarque xaerolib 1.4.3 : la derniere (1.8.3) a retire des methodes et plante au lancement
        implementation("xaero.lib:xaerolib-fabric-26.2") { version { strictly("1.4.3") } }
    }

    // Faux joueurs pour tester le multijoueur avec un seul client (/player Bob spawn), hors du jar publié
    modLocalRuntime("maven.modrinth:carpet:${v.carpet}")
}

tasks.withType<ProcessResources>().configureEach {
    val proprietes = mapOf(
        "version" to project.version,
        "minecraft" to "~$mc",
        "java" to ">=${v.java}",
        "loader" to ">=${v.loader}",
        "javaLevel" to v.java,
    )
    inputs.properties(proprietes)
    filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(proprietes) }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = v.java
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(v.java) }
    withSourcesJar()
    sourceCompatibility = JavaVersion.toVersion(v.java)
    targetCompatibility = JavaVersion.toVersion(v.java)
}

tasks.withType<JavaExec>().configureEach {
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
}

loomx.modJar.configure {
    archiveBaseName = "XaeroHeadTracker"
    archiveClassifier = "fabric-$mc"
    archiveVersion = project.version.toString()
    from(rootProject.file("LICENSE")) {
        rename { "${it}_XaeroHeadTracker" }
    }
}
