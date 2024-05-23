plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.tinkerpop:tinkergraph-gremlin:3.6.2")
}

tasks.jar {
    archiveFileName.set("${rootProject.name}.jar")
    destinationDirectory.set(rootDir)
}