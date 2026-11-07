import java.io.FileOutputStream
import java.util.zip.ZipFile

import net.fabricmc.loom.configuration.ide.RunConfigSettings

// The build of every version project under versions/ (settings.gradle.kts). `platform` says which
// game version and loader this project is; the source it compiles is the main project's, run
// through the preprocessor (root.gradle.kts).
plugins {
	id("gg.essential.multi-version")
	id("gg.essential.defaults")
}

// Calls that moved to another object between game versions are written once and rewritten by
// the preprocessor from the patterns in client/platform/ClientPatterns.
preprocess {
	patternAnnotation.set("com.mopicmp.mosaicary.client.platform.Pattern")
	// GENDER: whether the project is compiled against a line of Female Gender Mod (see the
	// dependencies below), so that only there the code joining in its drawing names its classes.
	vars.put("GENDER", if (findProperty("gender_mod_version") != null) 1 else 0)
}

val modVersion = property("mod_version").toString()
version = modVersion
group = property("maven_group").toString()
// The game versions a jar is for in the loaders' metadata: its own and its patch releases, up to
// the next one (26.3 -> [26.3,26.4), 1.21.1 -> [1.21.1,1.21.2)), and the Java it needs, which the
// mixin configurations name too. Placeholders of their own, as the toolkit expands ${...} itself.
val mcNext = platform.mcVersionStr.split(".").let { it.dropLast(1) + (it.last().toInt() + 1).toString() }.joinToString(".")
val javaMajor = platform.javaVersion.majorVersion
// NeoForge is numbered after the game without its leading "1." (1.21.11 -> 21.11) until 26.1, and
// after the game itself since (26.3).
val neoForgeMin = if (platform.isUnobfuscated) platform.mcVersionStr else platform.mcVersionStr.removePrefix("1.")
// Forge before NeoForge loads a mod's assets only with a pack.mcmeta of the game's resource pack
// format; the other loaders make their own and get none, nor Forge's mods.toml.
val packFormat = mapOf(1_20_01 to 15, 1_19_02 to 9, 1_18_02 to 8, 1_16_05 to 6)[platform.mcVersion]
// Fabric API's mod id: "fabric" in its releases for 1.16.5 and before, "fabric-api" since.
val fabricApiId = if (platform.mcVersion < 11700) "fabric" else "fabric-api"
// Forge's own number, which is also its mod loader's (1.20.1-47.4.28 -> 47), for the least one mods.toml asks for.
val forgeMin = findProperty("essential.defaults.loom.forge")?.toString()?.substringAfterLast(':')?.substringAfter('-')?.substringBefore('.') ?: ""
tasks.withType<ProcessResources>().configureEach {
	inputs.property("mcNext", mcNext)
	inputs.property("javaMajor", javaMajor)
	inputs.property("neoForgeMin", neoForgeMin)
	inputs.property("forgeMin", forgeMin)
	inputs.property("fabricApiId", fabricApiId)
	filesMatching(listOf("META-INF/neoforge.mods.toml", "META-INF/mods.toml", "fabric.mod.json", "*.mixins.json")) {
		filter { it.replace("@MC_NEXT@", mcNext).replace("@JAVA@", javaMajor).replace("@NEOFORGE_MIN@", neoForgeMin).replace("@FORGE_MIN@", forgeMin).replace("@FABRIC_API@", fabricApiId) }
	}
	if (platform.isForge) {
		inputs.property("packFormat", packFormat.toString())
		filesMatching("pack.mcmeta") {
			filter { it.replace("@PACK_FORMAT@", packFormat?.toString() ?: error("no resource pack format for ${platform.mcVersionStr}")) }
		}
	} else {
		exclude("pack.mcmeta", "META-INF/mods.toml", "mosaicary.forge.mixins.json")
	}
}

// The game version and loader go in the file name, not in the mod's own version, which the site
// sees in the User-Agent.
base.archivesName.set("mosaicary-$platform")

val webpVersion = property("webp_version").toString()
val clientHeapMin = property("client_heap_min").toString()
val clientHeapMax = property("client_heap_max").toString()
// The repository root, where tmp/ holds screenshots and models downloaded for tests.
val workspace = rootProject.projectDir.parentFile.absolutePath

repositories {
	mavenCentral()
	// Mod Menu, for its "Configure" button (client/compat/ModMenuEntry); compiled against only.
	maven("https://maven.terraformersmc.com/releases/")
	// Female Gender Mod, from Modrinth's own Maven (client/mixin/GenderLayerMixin); compiled against only.
	maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
}

// Two source sets on Fabric.
//
// `main` is the common half: it loads on clients and on servers. It is almost empty for now and
// is where an optional server part will go, one that relays worn models between players as
// packets when the server has the mod.
//
// `client` is everything else: the catalogue, the site client, the model reader and the renderer.
// The client must work fully on a server that does not have the mod at all, so nothing in
// `client` may depend on the server half answering.
//
// Loom splits them only from 1.18 on, where the game's server comes as a jar of its own; before, Fabric
// has one source set for both halves, as NeoForge and Forge have (below).
val splitSources = platform.isFabric && platform.mcVersion >= 11800
if (splitSources) {
	loom {
		splitEnvironmentSourceSets()
		mods {
			create("mosaicary") {
				sourceSet(sourceSets.main.get())
				sourceSet(sourceSets.getByName("client"))
			}
		}
	}
}

// Loom looks at a run's environment the moment the run is added, and in this build the run's task
// is made at once, so a run of our own is configured in full before it is added.
fun NamedDomainObjectContainer<RunConfigSettings>.addRun(name: String, configure: RunConfigSettings.() -> Unit) {
	val run = objects.newInstance(RunConfigSettings::class.java, project, name)
	run.configure()
	add(run)
}

// One mod and one jar on NeoForge and Forge, and on Fabric before 1.18, which have no split source sets:
// the client half compiles against the common half and is packed with it.
if (!splitSources) {
	val main = sourceSets.main.get()
	val client = sourceSets.create("client") {
		compileClasspath += main.compileClasspath + main.output
		runtimeClasspath += main.runtimeClasspath + main.output
	}
	// The preprocessor reads the class path from the configuration (see the game tests below).
	configurations.named(client.compileClasspathConfigurationName) {
		extendsFrom(configurations.getByName(main.compileClasspathConfigurationName))
	}
	dependencies { client.compileOnlyConfigurationName(main.output) }
	loom {
		mods {
			create("mosaicary") {
				sourceSet(main)
				sourceSet(client)
			}
		}
	}
	tasks.jar { from(client.output) }
	// The development launcher puts a mod together from the folders Loom names for it (MOD_CLASSES).
	// Loom names a run's own mods when it has any, and its runs here start with one made of the main
	// source set alone, which leaves the client half out; each run gets the whole mod instead. A run has
	// mods of its own only on Forge and NeoForge; Fabric takes the mod made above.
	if (!platform.isFabric) loom.runs.configureEach {
		mods.clear()
		mods.create("mosaicary") {
			sourceSet(main)
			sourceSet(client)
		}
	}
	// NeoForge lists other mods' outdated metadata (EMF and ETF still use `logoFile`) on a screen
	// that waits for a click; it does so only outside production, so players never see it.
	if (platform.isNeoForge) loom.runs.configureEach { property("neoforge.warnings.logofile.hide", "true") }
	// A run builds only the main half by itself; the client half has to be up to date too.
	tasks.named { it.startsWith("run") }.configureEach { dependsOn(client.classesTaskName) }
	// Forge before NeoForge takes a mod's mixins from the jar's manifest, not from its mods.toml.
	if (platform.isForge) loom.forge.mixinConfig("mosaicary.mixins.json", "mosaicary.client.mixins.json", "mosaicary.forge.mixins.json")
}

// Two clients and a server with the mod.
//
// Each client has its own run directory, because both write options, logs and the mod's cache to
// disk, and its own name, because a development server has no Microsoft session to tell two
// players apart by.
loom {
	runs {
		named("client") {
			configName = "Client - Alice"
			runDir("run")
			programArgs("--username", "Alice")
			vmArgs("-Xms$clientHeapMin", "-Xmx$clientHeapMax")
			// `-PquickPlay=<world>` starts straight into that world of run/saves, and `-PdevLan=<port>`
			// opens it to the network on that port (client/DevLan), for a second client to join with
			// `--quickPlayMultiplayer localhost:<port>` (tools/dev-client.mjs).
			findProperty("quickPlay")?.let { programArgs("--quickPlaySingleplayer", it.toString()) }
			findProperty("devLan")?.let { vmArg("-Dmosaicary.devLan=$it") }
		}
		addRun("client2") {
			client()
			configName = "Client - Bob"
			runDir("run2")
			programArgs("--username", "Bob")
			vmArgs("-Xms$clientHeapMin", "-Xmx$clientHeapMax")
		}
		named("server") {
			configName = "Server with the mod"
			runDir("run-server")
			vmArgs("-Xms1G", "-Xmx1G")
		}
		// The visual check: opens the catalogue at startup, takes a screenshot of the grid and of
		// one detail view once the previews have loaded, writes them to tmp/mosaicary-shots at the
		// repository root, and quits.
		addRun("shots") {
			client()
			configName = "Client - screenshots"
			runDir("run")
			// Large enough for GUI scale 4; the game caps the scale by the window size.
			programArgs("--username", "Alice", "--width", "1920", "--height", "1080")
			vmArgs("-Xms$clientHeapMin", "-Xmx$clientHeapMax", "-Dmosaicary.shots=$workspace/tmp/mosaicary-shots")
			// `-PshotsScales=2,3,4` repeats the round at each GUI scale; `-PshotsModel=<id>`
			// photographs only that model's detail view; `-PshotsFrames=rest@0,post@0.25` adds a
			// paused frame of each named animation; `-PshotsView=blockbench` shows those frames
			// from Blockbench's opening angle. Those are for the model catalogue's round, `-PshotsModels=true`;
			// without it the run photographs the title screen and the skin wardrobe, and with
			// `-PshotsServer=host:port` goes through the wardrobe on a server (client/Shots);
			// `-PshotsTurns=30,90,0@-50` also takes the opened skin's figure turned (and after @ tilted) by those degrees;
			// `-PshotsGender=true` makes a world and takes Female Gender Mod's body there instead (client/Shots).
			for (name in listOf("shotsScales", "shotsModel", "shotsFrames", "shotsView", "shotsModels", "shotsServer", "shotsSearch", "shotsTurns",
					"shotsGender")) {
				findProperty(name)?.let { vmArg("-Dmosaicary.$name=$it") }
			}
			// Other mods for the round, as for the game test: a folder of jars, read by Fabric Loader.
			findProperty("testMods")?.let { vmArg("-Dfabric.addMods=$it") }
		}
	}
}

// A client game test (`gradlew runClientGameTest`): starts the game, creates a world and drives
// the catalogue with real key presses and mouse input through Fabric's client game test API,
// taking screenshots on the way. It checks what the screenshot run cannot: the key, the pause
// menu button and dragging the preview. The test mod lives in src/gametest and is not part of the
// released jar.
//
// Set up here rather than with Loom's `fabricApi.configureTests`: in essential-gradle-toolkit's Loom
// that adds its run before giving it an environment, and the build stops on it. This is what it
// does, for the client test only: a source set and a mod of its own, a run that inherits the
// client's with Fabric API's switches, and an empty run folder each time.
//
// Fabric API has the client game test API from 1.21.4 on; older versions are checked with the
// screenshot run (`runShots`) instead.
if (platform.isFabric && platform.mcVersion >= 12104) {
	val client = sourceSets.getByName("client")
	val gametest = sourceSets.create("gametest") {
		compileClasspath += client.compileClasspath + sourceSets.main.get().output + client.output
		runtimeClasspath += client.runtimeClasspath + sourceSets.main.get().output + client.output
	}
	// The preprocessor reads a source set's class path from its configuration, not from the files
	// above, and without it cannot tell what the tests' names refer to and leaves them unconverted.
	configurations.named(gametest.compileClasspathConfigurationName) {
		extendsFrom(configurations.getByName(client.compileClasspathConfigurationName))
	}
	dependencies {
		gametest.compileOnlyConfigurationName(sourceSets.main.get().output)
		gametest.compileOnlyConfigurationName(client.output)
	}
	loom {
		mods.create("mosaicary-gametest") { sourceSet(gametest) }
		runs.addRun("clientGameTest") {
			inherit(runs.getByName("client"))
			configName = "Client game test"
			property("fabric.client.gametest")
			property("fabric.client.gametest.testModResourcesPath", gametest.resources.srcDirs.first().absolutePath)
			runDir("build/run/clientGameTest")
			source(gametest)
		}
	}
	val clearGameTestRun = tasks.register<Delete>("deleteGameTestRunDir") {
		delete(file("build/run/clientGameTest"))
	}
	tasks.named { it == "runClientGameTest" }.configureEach { dependsOn(clearGameTestRun) }
}

// The libraries the jar brings with it go in as jars of their own, which the loader opens (jar-in-jar);
// Forge before 1.18 has none, so there their classes go into the mod's jar itself, and each one's licence
// files under META-INF/licenses/<library>.
val nestsJars = !(platform.isForge && platform.mcVersion < 11800)
val unpacked = configurations.create("unpackedIntoJar") { isTransitive = false }
fun DependencyHandler.bundle(artifact: String) {
	if (nestsJars) add("include", artifact) else add(unpacked.name, artifact)
}

dependencies {
	if (platform.isFabric) {
		"modImplementation"("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
		// Mod Menu's interface, to open the settings from its list of mods. Not shipped and not
		// required: the entry point is only loaded when Mod Menu is there. On the obfuscated game
		// versions it has to be remapped to the names the mod is compiled against.
		val modMenu = "com.terraformersmc:modmenu:${property("modmenu_version")}"
		if (platform.isUnobfuscated) "clientCompileOnly"(modMenu) else if (splitSources) "modClientCompileOnly"(modMenu) else "modCompileOnly"(modMenu)
	}

	// Female Gender Mod, whose drawing of the breasts the client joins in when it is there
	// (client/mixin/GenderLayerMixin). Each line of it draws them its own way, so a project names
	// the version it is compiled against in its gradle.properties; one without names none, and its
	// jar knows nothing of the mod. Not shipped and not required.
	findProperty("gender_mod_version")?.let { gender ->
		val genderMod = "maven.modrinth:female-gender:$gender"
		val configuration = if (platform.isUnobfuscated) "clientCompileOnly" else if (splitSources) "modClientCompileOnly" else "modCompileOnly"
		add(configuration, genderMod) { (this as ExternalModuleDependency).isTransitive = false }
	}
	// Other mods for the runs only, from Modrinth by project and version id or as jar files
	// (`-PrunMods=female-gender:R1d8TWqL,/path/TRender.jar`), turned to the names the game runs with
	// here. For Forge and NeoForge, whose loaders read no folder of jars as Fabric Loader does for
	// `-PtestMods`, and where a released Forge jar has names of its own. Jars nested in a mod are
	// not taken out of it here, so they are given as files of their own; one that is no mod (no
	// mods.toml) is a library, which Forge loads only from its own configuration.
	findProperty("runMods")?.toString()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach { mod ->
		val configuration = if (platform.isUnobfuscated) "runtimeOnly" else "modLocalRuntime"
		if (!mod.endsWith(".jar")) {
			add(configuration, "maven.modrinth:$mod") { (this as ExternalModuleDependency).isTransitive = false }
		} else if (platform.isForge && ZipFile(mod).use { it.getEntry("META-INF/mods.toml") == null }) {
			add("forgeRuntimeLibrary", files(mod))
		} else {
			add(configuration, files(mod))
		}
	}

	// A WebP decoder, for the preview pictures the site serves.
	//
	// The site stores every card picture as WebP, and the game's own decoder (stb_image) reads
	// PNG, JPEG, GIF and BMP but not WebP. TwelveMonkeys is pure Java, so one jar works on every
	// platform; the same decoder is already in use in the NPC Studio mod next to this one.
	val webp = "com.twelvemonkeys.imageio:imageio-webp:$webpVersion"
	if (splitSources) "clientImplementation"(webp) else "implementation"(webp)
	// Listed one by one because jar-in-jar does not follow transitive dependencies, and a decoder
	// missing half of itself would only fail when the catalogue is opened. The WebP reader's own
	// pom lists imageio-metadata too; leaving it out of the jar would only show when a picture
	// with metadata is read.
	for (artifact in listOf(
		webp,
		"com.twelvemonkeys.imageio:imageio-core:$webpVersion",
		"com.twelvemonkeys.imageio:imageio-metadata:$webpVersion",
		"com.twelvemonkeys.common:common-lang:$webpVersion",
		"com.twelvemonkeys.common:common-io:$webpVersion",
		"com.twelvemonkeys.common:common-image:$webpVersion",
	)) {
		bundle(artifact)
	}

	// JOML, which the model reader and the figure drawing do their matrices with. The game ships it
	// from 1.19.3 on; before, the jar brings its own, the version 1.20.1 ships.
	if (platform.mcVersion < 11903) {
		val joml = "org.joml:joml:1.10.5"
		if (splitSources) "clientImplementation"(joml) else "implementation"(joml)
		bundle(joml)
	}

	// The model reader and the site client have no Minecraft types in them, so they are checked
	// with plain JUnit and no game running.
	testImplementation(platform("org.junit:junit-bom:5.11.4"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// TwelveMonkeys is under the BSD licence, which wants its notice in every copy handed out in binary
// form, and its jars carry none, so the jar carries it. The mod's own licence goes with it: the
// LGPL wants its text, and the GPL's it builds on, in every copy.
tasks.jar {
	from(rootProject.file("licenses/twelvemonkeys")) { into("META-INF/licenses/twelvemonkeys") }
	from(files(rootProject.file("LICENSE"), rootProject.file("COPYING"), rootProject.file("COPYING.LESSER"))) { into("META-INF/licenses/mosaicary") }
}

if (!nestsJars) {
	tasks.jar {
		for (library in unpacked.resolve()) {
			from(zipTree(library)) {
				exclude("module-info.class", "META-INF/**")
			}
			from(zipTree(library)) {
				include("META-INF/LICENSE*", "META-INF/NOTICE*")
				eachFile { path = "META-INF/licenses/${library.nameWithoutExtension}/$name" }
				includeEmptyDirs = false
			}
		}
	}
}

// The tested code lives in the client source set, so its output and class path go on the tests'
// class path.
sourceSets.named("test") {
	val client = sourceSets.getByName("client")
	compileClasspath += client.compileClasspath + client.output
	runtimeClasspath += client.runtimeClasspath + client.output
}

tasks.named<Test>("test") {
	useJUnitPlatform()
	// Models downloaded from the live site for testing live in tmp/ at the repository root and
	// never in this project. Tests that need them skip themselves when the folder is absent.
	systemProperty("mosaicary.liveModels", "$workspace/tmp/mosaicary-live")
	// Builds (Litematica, WorldEdit and Axiom samples, and what the site wrote from them) are in
	// tmp/ too; see BuildsTest.
	systemProperty("mosaicary.tmp", "$workspace/tmp")
	// `gradlew test -PsiteLive` also runs read-only requests against mosaicary.com.
	val siteLive = project.hasProperty("siteLive")
	if (siteLive) systemProperty("mosaicary.siteLive", "true")
	outputs.upToDateWhen { !siteLive }
	testLogging {
		events("passed", "failed", "skipped")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
		showStandardStreams = true
	}
}

// Before 1.17 the game runs on Java 8, and the toolkit would compile for it. The mod is written for the
// Java of the newer versions, so it is compiled with Java 17 as on 1.18.2, and the jar is brought down to
// Java 8 afterwards: JvmDowngrader rewrites the classes (records, switch expressions, patterns) and puts in
// the jar its own copies of the Java 9 to 17 classes and methods they call, under a prefix of the mod's,
// before Loom gives the jar the loader's names. One source for every version, written as for the newest.
//
// It runs as a process of its own, from its own jar: inside Gradle it shares a class loader with Loom,
// whose older commons-compress it then calls and fails on. A class or method it has no copy of would only
// fail in the game, on Java 8, when that code runs; it says so in its output, and the build stops on it.
val downgraded = platform.mcVersion < 11700
if (downgraded) {
	val java17 = JavaLanguageVersion.of(17)
	tasks.withType<JavaCompile>().configureEach {
		javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(java17) })
		options.release.set(17)
	}
	// The tests run on the classes as compiled, before they are brought down: on Java 17, with a Gson
	// that reads a string by a static method as the newer versions' does (1.16.5 has 2.8.0).
	tasks.named<Test>("test") {
		javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(java17) })
	}
	dependencies { "testImplementation"("com.google.code.gson:gson:2.10.1") }

	// The copies are JvmDowngrader's, under the LGPL: its licence goes in the jar with them.
	tasks.jar {
		from(rootProject.file("licenses/jvmdowngrader")) { into("META-INF/licenses/jvmdowngrader") }
	}
	val jvmdg = configurations.detachedConfiguration(dependencies.create("xyz.wagyourtail.jvmdowngrader:jvmdowngrader:2.0.1:all"))
	jvmdg.isTransitive = false
	val compiled = tasks.jar.flatMap { it.archiveFile }
	val java8Jar = layout.buildDirectory.file("devlibs/mosaicary-$platform-$modVersion-java8.jar")
	val report = layout.buildDirectory.file("jvmdg/report.txt")
	val gameClasspath = sourceSets.getByName("client").compileClasspath
	val downgrade = tasks.register<JavaExec>("downgradeToJava8") {
		description = "Brings the compiled jar down to Java 8 and puts the Java API copies it needs in it."
		inputs.file(compiled)
		inputs.files(gameClasspath)
		outputs.file(java8Jar)
		javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(java17) })
		// It keeps a cache of its API copies in .jvmdg of the folder it runs in; in build/, that is.
		workingDir = layout.buildDirectory.dir("jvmdg").get().asFile.also { it.mkdirs() }
		classpath(jvmdg)
		mainClass.set("xyz.wagyourtail.jvmdg.cli.Main")
		argumentProviders.add(CommandLineArgumentProvider {
			listOf("--noColor", "--classVersion", "52",
				"downgrade", "--target", compiled.get().asFile.absolutePath, "-",
				"--classpath", gameClasspath.files.joinToString(File.pathSeparator),
				"shade", "--prefix", "com/mopicmp/mosaicary/jvmdg", "--target", "-", java8Jar.get().asFile.absolutePath)
		})
		val log = report.get().asFile
		doFirst {
			log.parentFile.mkdirs()
			val out = FileOutputStream(log)
			standardOutput = out
			errorOutput = out
		}
		doLast {
			val uncovered = log.readLines().filter { "is not covered by the api stubs" in it }.map { it.trim() }.distinct()
			if (uncovered.isNotEmpty()) {
				throw GradleException("Java 8 has no copy of what this code calls (see ${log}):\n" + uncovered.joinToString("\n"))
			}
		}
	}
	tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
		inputFile.set(java8Jar)
		dependsOn(downgrade)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	// The compiler runs in a worker with its own heap, smaller than the daemon's. Compiling
	// against the whole Minecraft jar exhausts the default, and the failure says "worker daemon
	// finished with non-zero exit value" without mentioning memory.
	options.forkOptions.memoryMaximumSize = "2g"
	// All of a version's errors at once rather than the first hundred, when a new version is added.
	options.compilerArgs.addAll(listOf("-Xmaxerrs", "2000"))
}

// Other mods for a game test run, without adding them to the mod's dependencies: a folder of jars
// (`-PtestMods=/path`, read by Fabric Loader as fabric.addMods) and resource packs the test puts
// in the run's folder (`-PtestPacks=/path`). For checking the Mod Menu button and live eyes with
// Entity Model Features, Entity Texture Features and Fresh Moves+.
tasks.named { it == "runClientGameTest" }.configureEach {
	this as JavaExec
	findProperty("testMods")?.let { jvmArgs("-Dfabric.addMods=$it") }
	findProperty("testPacks")?.let { jvmArgs("-Dmosaicary.testPacks=$it") }
	findProperty("testSearch")?.let { jvmArgs("-Dmosaicary.testSearch=$it") }
	findProperty("testSkin")?.let { jvmArgs("-Dmosaicary.testSkin=$it") }
	// All the languages, or only those named: -PtestLanguages=fr_fr,pl_pl.
	if (project.hasProperty("testLanguages")) jvmArgs("-Dmosaicary.testLanguages=${findProperty("testLanguages").toString().ifBlank { "true" }}")
	findProperty("testOnly")?.let { jvmArgs("-Dmosaicary.testOnly=$it") }
	findProperty("testSite")?.let { jvmArgs("-Dmosaicary.testSite=$it", "-Dmosaicary.testSiteKey=${property("testSiteKey")}") }
}
