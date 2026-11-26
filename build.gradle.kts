// ==============================================================================
// thz-lang-cobol — Módulo de Interoperabilidade, Transpilação e LLVM AOT para COBOL
// ==============================================================================

plugins {
    `java-library`
    `maven-publish`
}

val repoRoot = rootProject.projectDir.resolve("../thz-lang/")
val versionFile = if (file("version.txt").exists()) file("version.txt") else repoRoot.resolve("version.txt")
val thzVersion = if (versionFile.exists()) versionFile.readText().trim() else "0.1.0"

group = "thz.lang"
version = thzVersion

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
}

dependencies {
    implementation("thz.lang:thz-core:$thzVersion")

    // Testes Automatizados
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}

tasks.test {
    useJUnitPlatform()
}
