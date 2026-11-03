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
tasks.withType<ProcessResources>().configureEach {
	inputs.property("mcNext", mcNext)
	inputs.property("javaMajor", javaMajor)
	inputs.property("neoForgeMin", neoForgeMin)
	filesMatching(listOf("META-INF/neoforge.mods.toml", "fabric.mod.json", "*.mixins.json")) {
		filter { it.replace("@MC_NEXT@", mcNext).replace("@JAVA@", javaMajor).replace("@NEOFORGE_MIN@", neoForgeMin) }
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
if (platform.isFabric) {
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

// One mod and one jar on NeoForge and Forge, which have no split source sets: the client half
// compiles against the common half and is packed with it.
if (!platform.isFabric) {
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
	// source set alone, which leaves the client half out; each run gets the whole mod instead.
	loom.runs.configureEach {
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
			// `-PshotsServer=host:port` goes through the wardrobe on a server (client/Shots).
			for (name in listOf("shotsScales", "shotsModel", "shotsFrames", "shotsView", "shotsModels", "shotsServer", "shotsSearch")) {
				findProperty(name)?.let { vmArg("-Dmosaicary.$name=$it") }
			}
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

dependencies {
	if (platform.isFabric) {
		"modImplementation"("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
		// Mod Menu's interface, to open the settings from its list of mods. Not shipped and not
		// required: the entry point is only loaded when Mod Menu is there. On the obfuscated game
		// versions it has to be remapped to the names the mod is compiled against.
		val modMenu = "com.terraformersmc:modmenu:${property("modmenu_version")}"
		if (platform.isUnobfuscated) "clientCompileOnly"(modMenu) else "modClientCompileOnly"(modMenu)
	}

	// A WebP decoder, for the preview pictures the site serves.
	//
	// The site stores every card picture as WebP, and the game's own decoder (stb_image) reads
	// PNG, JPEG, GIF and BMP but not WebP. TwelveMonkeys is pure Java, so one jar works on every
	// platform; the same decoder is already in use in the NPC Studio mod next to this one.
	val webp = "com.twelvemonkeys.imageio:imageio-webp:$webpVersion"
	if (platform.isFabric) "clientImplementation"(webp) else "implementation"(webp)
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
		"include"(artifact)
	}

	// The model reader and the site client have no Minecraft types in them, so they are checked
	// with plain JUnit and no game running.
	testImplementation(platform("org.junit:junit-bom:5.11.4"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
	if (project.hasProperty("testLanguages")) jvmArgs("-Dmosaicary.testLanguages=true")
	findProperty("testSite")?.let { jvmArgs("-Dmosaicary.testSite=$it", "-Dmosaicary.testSiteKey=${property("testSiteKey")}") }
}
