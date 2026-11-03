// Offline variants share the checked-in snapshot without changing normal packages.
val prepareOfflineAssets = tasks.register<Exec>("prepareOfflineAssets") {
    workingDir(rootDir)
    commandLine("node", "tools/offline-snapshot.mjs", "package")
    inputs.dir(rootProject.file("offline-data"))
    inputs.file(rootProject.file("tools/offline-snapshot.mjs"))
    inputs.files(fileTree(rootProject.file("web/src/android/railway")) { include("*.js") })
    outputs.dir(layout.buildDirectory.dir("generated/offline-assets"))
}

tasks.matching {
    it.name == "preCiOfflineBuild" || it.name == "preReleaseOfflineBuild" ||
        it.name.contains("lint", ignoreCase = true) && it.name.contains("Offline")
}.configureEach { dependsOn(prepareOfflineAssets) }
