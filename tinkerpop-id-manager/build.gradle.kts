plugins {
    java
}

version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    implementation("org.apache.tinkerpop:tinkergraph-gremlin:3.4.10")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
