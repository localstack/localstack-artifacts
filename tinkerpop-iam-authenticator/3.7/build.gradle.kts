plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.tinkerpop:gremlin-server:3.7.2")
}

tasks.jar {
    archiveFileName.set("${rootProject.name}-37.jar")
    destinationDirectory.set(rootDir)
}