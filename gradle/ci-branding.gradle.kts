// Generate variant-only branding from the maintained language catalogs.
val generateCiBranding = tasks.register("generateCiBranding") {
    val ciBrandingDirectory = layout.buildDirectory.dir("generated/ci-branding")
    val ciBrandingCatalogs = fileTree("src/main/res") { include("values*/strings.xml") }
    val ciBrandingChangelogs = rootProject.file("changelogs")
    inputs.files(ciBrandingCatalogs)
    inputs.dir(ciBrandingChangelogs)
    outputs.dir(ciBrandingDirectory)
    doLast {
        val output = ciBrandingDirectory.get().asFile
        output.deleteRecursively()
        val stringEntry = Regex("""<string\b[^>]*>.*?</string>""", RegexOption.DOT_MATCHES_ALL)
        ciBrandingCatalogs.forEach { catalog ->
            val entries = stringEntry.findAll(catalog.readText()).map { it.value }
                .filter { "CRTicket" in it && "https://" !in it && "http://" !in it }
                .map { it.replace(Regex("CRTicket(?! CI)"), "CRTicket CI") }.toList()
            if (entries.isNotEmpty()) {
                val target = output.resolve("res/${catalog.parentFile.name}/strings.xml")
                target.parentFile.mkdirs()
                target.writeText("<resources>\n${entries.joinToString("\n")}\n</resources>\n")
            }
        }
        ciBrandingChangelogs.listFiles().orEmpty()
            .filter { it.extension == "md" && it.name != "README.md" }.forEach { source ->
                val target = output.resolve("assets/changelogs/${source.name}")
                target.parentFile.mkdirs()
                target.writeText(source.readText().replace(Regex("CRTicket(?! CI|[-A-Za-z])"), "CRTicket CI"))
            }
    }
}

tasks.matching { it.name == "preCiBuild" }.configureEach {
    dependsOn(generateCiBranding)
}
