plugins {
	id("gg.essential.multi-version.root")
}

// The versions the preprocessor converts between. A link joins two projects that share either the
// game version (another loader) or the naming scheme (another game version); a file on a link holds
// the renames it cannot work out by itself.
preprocess {
	val fabric26_3 = createNode("26.3-fabric", 26_03_00, "official")
	val neoForge26_3 = createNode("26.3-neoforge", 26_03_00, "official")

	neoForge26_3.link(fabric26_3)
}
