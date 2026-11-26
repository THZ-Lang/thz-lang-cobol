plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "thz-lang-cobol"

// Composite Build: em desenvolvimento, resolve thz.lang:thz-core direto do núcleo vizinho
if (file("../thz-core-jvm").exists()) {
    includeBuild("../thz-core-jvm")
}
