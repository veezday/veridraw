plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":shared"))
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.bootJar {
    enabled = false
}
tasks.jar {
    enabled = true
}