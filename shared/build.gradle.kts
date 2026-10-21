dependencies {
    api("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    compileOnly("io.projectreactor:reactor-core")
    compileOnly("com.fasterxml.jackson.core:jackson-annotations")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}