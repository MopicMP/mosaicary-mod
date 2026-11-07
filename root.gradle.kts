plugins {
	id("gg.essential.multi-version.root")
}

// The versions the preprocessor converts between. A link joins two projects that share either the
// game version (another loader) or the loader (another game version); a file on a link holds the
// renames it cannot work out by itself.
preprocess {
	// Only the projects settings.gradle.kts set up (all, or those `-Pnodes` asked for and the ones
	// their code is derived through), and the links between them: a link to a project left out is
	// skipped. The first node made is the main project, where the preprocessor starts.
	fun node(project: String, mcVersion: Int) =
		if (findProject(":$project") != null) createNode(project, mcVersion, "official") else null
	fun com.replaymod.gradle.preprocess.Node?.link(other: com.replaymod.gradle.preprocess.Node?, extraMappings: File? = null) {
		if (this != null && other != null) link(other, extraMappings)
	}

	val fabric26_3 = node("26.3-fabric", 26_03_00)
	val neoForge26_3 = node("26.3-neoforge", 26_03_00)
	val fabric1_21_11 = node("1.21.11-fabric", 1_21_11)
	val neoForge1_21_11 = node("1.21.11-neoforge", 1_21_11)
	val fabric1_21_1 = node("1.21.1-fabric", 1_21_01)
	val neoForge1_21_1 = node("1.21.1-neoforge", 1_21_01)
	val fabric1_20_1 = node("1.20.1-fabric", 1_20_01)
	val forge1_20_1 = node("1.20.1-forge", 1_20_01)
	val fabric1_19_2 = node("1.19.2-fabric", 1_19_02)
	val forge1_19_2 = node("1.19.2-forge", 1_19_02)
	val fabric1_18_2 = node("1.18.2-fabric", 1_18_02)
	val forge1_18_2 = node("1.18.2-forge", 1_18_02)
	val fabric1_16_5 = node("1.16.5-fabric", 1_16_05)
	val forge1_16_5 = node("1.16.5-forge", 1_16_05)

	neoForge26_3.link(fabric26_3)
	fabric26_3.link(fabric1_21_11, file("versions/26.3-1.21.11.txt"))
	// Each loader's versions follow one another, so that code written for one loader only is renamed
	// from version to version too; the other loader's branches stay commented out along the chain.
	neoForge26_3.link(neoForge1_21_11, file("versions/26.3-1.21.11.txt"))
	fabric1_21_11.link(fabric1_21_1, file("versions/1.21.11-1.21.1.txt"))
	neoForge1_21_11.link(neoForge1_21_1, file("versions/1.21.11-1.21.1.txt"))
	fabric1_21_1.link(fabric1_20_1, file("versions/1.21.1-1.20.1.txt"))
	// Forge is not NeoForge's predecessor in the code: what it needs is written in branches of its
	// own, so it follows Fabric of the same game version, where no names change.
	fabric1_20_1.link(forge1_20_1)
	fabric1_20_1.link(fabric1_19_2, file("versions/1.20.1-1.19.2.txt"))
	fabric1_19_2.link(forge1_19_2)
	fabric1_19_2.link(fabric1_18_2, file("versions/1.19.2-1.18.2.txt"))
	fabric1_18_2.link(forge1_18_2)
	fabric1_18_2.link(fabric1_16_5, file("versions/1.18.2-1.16.5.txt"))
	fabric1_16_5.link(forge1_16_5)
}
