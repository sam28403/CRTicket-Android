fun registerWebAssets(taskName: String, ci: Boolean) = tasks.register<Exec>(taskName) {
    group = "build"
    description = "Build variant-specific bundled web resources."
    workingDir(rootProject.file("web"))
    val output = layout.buildDirectory.dir(if (ci) "generated/web-ci/web" else "generated/web-public/web")
    environment("CRTICKET_CI", ci.toString())
    environment("CRTICKET_WEB_OUTPUT", output.get().asFile.absolutePath)
    environment("CRTICKET_SKIP_COMMON", ci.toString())
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        commandLine("cmd.exe", "/d", "/c", "npm.cmd", "run", "build")
    } else {
        commandLine("npm", "run", "build")
    }
    inputs.dir(rootProject.file("web/src"))
    inputs.dir(rootProject.file("web/public"))
    inputs.files(rootProject.fileTree("tools") { include("*.mjs") })
    inputs.files(
        rootProject.file("gradle.properties"),
        rootProject.file("web/package.json"),
        rootProject.file("web/package-lock.json"),
        rootProject.file("web/vite.config.js"),
        project.file("src/main/res/raw/cfca_ev_root.pem"),
    )
    outputs.dir(output)
    if (!ci) {
        outputs.file(project.file("src/main/assets/arrival-stations.json"))
        outputs.file(project.file("src/main/assets/sale-times.json"))
        outputs.dir(project.file("src/main/assets/speedometer-lib"))
    }
}
val buildWebAssets = registerWebAssets("buildWebAssets", false)
val buildCiWebAssets = registerWebAssets("buildCiWebAssets", true)
buildCiWebAssets.configure { dependsOn(buildWebAssets) }
tasks.matching { it.name == "preBuild" || it.name.contains("lint", ignoreCase = true) }.configureEach {
    dependsOn(buildWebAssets)
}
tasks.matching {
    it.name == "preCiBuild" || it.name == "preCiOfflineBuild" ||
        it.name.contains("lint", ignoreCase = true) && it.name.contains("Ci")
}.configureEach {
    dependsOn(buildCiWebAssets)
}
