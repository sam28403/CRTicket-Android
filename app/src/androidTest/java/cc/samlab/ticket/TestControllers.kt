package cc.samlab.ticket

/** Locate an initialized controller without relying on field names changed by R8. */
internal inline fun <reified T> MainActivity.testController(): T {
    for (field in MainActivity::class.java.declaredFields) {
        if (!Lazy::class.java.isAssignableFrom(field.type)) continue
        field.isAccessible = true
        val delegate = field.get(this) as? Lazy<*> ?: continue
        if (delegate.isInitialized() && delegate.value is T) return delegate.value as T
    }
    error("Initialized controller not found: ${T::class.java.name}")
}
