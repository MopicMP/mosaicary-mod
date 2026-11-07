pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
		maven("https://maven.fabricmc.net")
		maven("https://maven.architectury.dev/")
		maven("https://maven.minecraftforge.net")
		maven("https://maven.neoforged.net/releases/")
		maven("https://repo.essential.gg/repository/maven-public")
	}
	plugins {
		val egtVersion = "0.7.3"
		id("gg.essential.multi-version.root") version egtVersion
		id("gg.essential.multi-version") version egtVersion
		id("gg.essential.defaults") version egtVersion
	}
}

rootProject.name = "mosaicary"
rootProject.buildFileName = "root.gradle.kts"

// One project per game version and loader (docs/mod-versions-plan.md). Each has its folder under
// versions/, where its build output and its own gradle.properties live; all are configured by the
// one build.gradle.kts. The source is written for the main project (versions/mainProject) and the
// preprocessor derives the others from it, each from the one beside it here (as root.gradle.kts
// links them).
val derivedFrom = linkedMapOf(
	"26.3-fabric" to null,
	"26.3-neoforge" to "26.3-fabric",
	"1.21.11-fabric" to "26.3-fabric",
	"1.21.11-neoforge" to "26.3-neoforge",
	"1.21.1-fabric" to "1.21.11-fabric",
	"1.21.1-neoforge" to "1.21.11-neoforge",
	"1.20.1-fabric" to "1.21.1-fabric",
	"1.20.1-forge" to "1.20.1-fabric",
	"1.19.2-fabric" to "1.20.1-fabric",
	"1.19.2-forge" to "1.19.2-fabric",
	"1.18.2-fabric" to "1.19.2-fabric",
	"1.18.2-forge" to "1.18.2-fabric",
	"1.16.5-fabric" to "1.18.2-fabric",
	"1.16.5-forge" to "1.16.5-fabric",
)

// `-Pnodes=1.21.11-fabric,1.16.5-forge` sets up only those projects and the ones their code is
// derived through. Every project set up holds a game's worth of Loom in the daemon, and all
// fourteen took it 46 seconds and a gigabyte and a half of the machine's committed memory even
// for a build of one (tools/measure-build.sh, 2026-11-04).
val wanted = providers.gradleProperty("nodes").orNull?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
val included = if (wanted == null) derivedFrom.keys else buildSet {
	for (node in wanted) {
		require(node in derivedFrom) { "No project $node; there are ${derivedFrom.keys.joinToString()}" }
		var on: String? = node
		while (on != null) {
			add(on)
			on = derivedFrom[on]
		}
	}
}

derivedFrom.keys.filter { it in included }.forEach { version ->
	include(":$version")
	project(":$version").apply {
		projectDir = file("versions/$version")
		buildFileName = "../../build.gradle.kts"
	}
}
