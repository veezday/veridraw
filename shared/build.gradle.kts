dependencies {
    compileOnly("com.fasterxml.jackson.core:jackson-annotations")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.2")
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
    }
}