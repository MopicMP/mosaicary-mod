plugins {
	id("gg.essential.multi-version.root")
}

// The versions the preprocessor converts between. A link joins two projects that share either the
// game version (another loader) or the loader (another game version); a file on a link holds the
// renames it cannot work out by itself.
preprocess {
	val fabric26_3 = createNode("26.3-fabric", 26_03_00, "official")
	val neoForge26_3 = createNode("26.3-neoforge", 26_03_00, "official")
	val fabric1_21_11 = createNode("1.21.11-fabric", 1_21_11, "official")
	val neoForge1_21_11 = createNode("1.21.11-neoforge", 1_21_11, "official")
	val fabric1_21_1 = createNode("1.21.1-fabric", 1_21_01, "official")
	val neoForge1_21_1 = createNode("1.21.1-neoforge", 1_21_01, "official")

	neoForge26_3.link(fabric26_3)
	fabric26_3.link(fabric1_21_11, file("versions/26.3-1.21.11.txt"))
	// Each loader's versions follow one another, so that code written for one loader only is renamed
	// from version to version too; the other loader's branches stay commented out along the chain.
	neoForge26_3.link(neoForge1_21_11, file("versions/26.3-1.21.11.txt"))
	fabric1_21_11.link(fabric1_21_1, file("versions/1.21.11-1.21.1.txt"))
	neoForge1_21_11.link(neoForge1_21_1, file("versions/1.21.11-1.21.1.txt"))
}
