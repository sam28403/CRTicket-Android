# Only loaded for temporary full-device tests. Preserve the cross-APK test ABI.
-dontoptimize
-keep,allowoptimization class ** {
    public *;
    protected *;
}

