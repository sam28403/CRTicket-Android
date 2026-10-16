# Android WebView invokes these methods by their JavaScript-visible names.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
