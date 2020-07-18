plugins {
    java
}

group = "fr.theorozier"
version = "1.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.8.6")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(mapOf("Main-Class" to "fr.theorozier.rmcp.Main"))
    }
}