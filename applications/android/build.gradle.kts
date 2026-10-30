// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

// Formatting, checked by `spotlessCheck` (CI) and fixed by `spotlessApply`. The ktlint rules are
// set here, not in .editorconfig: Spotless's ktlint didn't pick that file up.
val ktlintRules = mapOf(
    // The closest ktlint style to how this code is already written, so adopting it moves little.
    "ktlint_code_style" to "intellij_idea",
    "max_line_length" to "140",
    // Composables are named like types (PascalCase), as Compose expects.
    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
    // Signatures stay as written: one parameter per line (data classes, Composables) or all on
    // one line is the author's call, and collapsing them would re-indent whole bodies for nothing.
    "ktlint_standard_function-signature" to "disabled",
    "ktlint_standard_class-signature" to "disabled",
    "ij_kotlin_allow_trailing_comma" to "false",
    "ij_kotlin_allow_trailing_comma_on_call_site" to "false"
)

spotless {
    kotlin {
        target("app/src/**/*.kt", "core/*/src/**/*.kt", "tools/*/src/**/*.kt")
        targetExclude("**/Secrets.kt") // gitignored, copied from its .example
        ktlint().editorConfigOverride(ktlintRules)
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts", "core/*/*.gradle.kts", "tools/*/*.gradle.kts")
        ktlint().editorConfigOverride(ktlintRules)
    }
}
