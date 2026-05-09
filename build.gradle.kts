plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    `maven-publish`
    // id("org.jetbrains.dokka") version "0.10.0" // disabled — incompatible with Gradle 9; not needed for composite build
}

group = "me.fungames"
version = "3.6.5"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

sourceSets {
    main {
        java.srcDir("src/main/kotlin")
    }
}

val sourcesJar by tasks.registering(Jar::class) {
    from(sourceSets.main.get().allSource)
    archiveClassifier.set("sources")
}

val javadocJar by tasks.registering(Jar::class) {
    from(tasks.javadoc)
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "JFortniteParse"
            groupId = project.group.toString()
            version = project.version.toString()
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)
        }
    }
}

dependencies {
    api("androidx.collection:collection-ktx:1.2.0")
    api("com.github.Amrsatrio:JOodle:master-SNAPSHOT")
    api("com.github.FabianFG:KotlinASTC:1.3")
    api("com.github.FabianFG:KotlinPointers:1.1")
    //api("com.github.FabianFG:KotlinSquish:1.0") // Somehow broken with alpha values
    api("com.github.memo33:jsquish:2.0.1")
    api("com.github.salomonbrys.kotson:kotson:2.5.0")
    api("com.google.code.gson:gson:2.9.0")
    api("com.tomgibara.bits:bits:2.1.0")
    api("io.github.microutils:kotlin-logging:3.0.0")
    api("net.java.dev.jna:jna:5.9.0")
    api("org.apache.logging.log4j:log4j-core:2.19.0")
    api("org.apache.logging.log4j:log4j-slf4j2-impl:2.19.0")
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2-native-mt")
    api("org.objenesis:objenesis:3.2")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        freeCompilerArgs.addAll(
            "-Xallow-result-return-type",
            "-Xno-call-assertions",
            "-Xno-param-assertions",
            "-Xno-receiver-assertions",
            "-opt-in=kotlin.ExperimentalUnsignedTypes"
        )
    }
}
