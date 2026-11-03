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
// preprocessor derives the others from it.
listOf(
	"26.3-fabric",
	"26.3-neoforge",
	"1.21.11-fabric",
	"1.21.11-neoforge",
	"1.21.1-fabric",
	"1.21.1-neoforge",
).forEach { version ->
	include(":$version")
	project(":$version").apply {
		projectDir = file("versions/$version")
		buildFileName = "../../build.gradle.kts"
	}
}
