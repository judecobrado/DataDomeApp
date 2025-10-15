plugins {
    // These are module-level plugins, not applied globally
    id("com.google.gms.google-services") version "4.4.3" apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
