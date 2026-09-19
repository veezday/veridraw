plugins {
    java
    id("com.diffplug.spotless") version "6.25.0"
    id("org.springframework.boot") version "4.1.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

subprojects {
    group = "io.veridraw"
    version = "0.1.0-SNAPSHOT"

    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "io.spring.dependency-management")


    repositories {
        mavenCentral()
    }

    spotless {
        java {
            importOrder()
            removeUnusedImports()
            palantirJavaFormat("2.38.0")
            formatAnnotations()
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }
}

//plugins {
//    java
//    id("org.springframework.boot") version "4.1.0"
//    id("io.spring.dependency-management") version "1.1.7"
//}
//
//group = "com.example"
//version = "0.0.1-SNAPSHOT"
//
//java {
//    toolchain {
//        languageVersion = JavaLanguageVersion.of(21)
//    }
//}
//
//repositories {
//    mavenCentral()
//}
//
//extra["springCloudVersion"] = "2025.1.2"
//
//dependencies {
//    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
//    implementation("org.springframework.boot:spring-boot-starter-micrometer-metrics")
//    implementation("org.springframework.boot:spring-boot-starter-validation")
//    implementation("org.springframework.boot:spring-boot-starter-webflux")
//    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j")
//    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
//    implementation("org.springframework.cloud:spring-cloud-stream")
//    runtimeOnly("io.micrometer:micrometer-registry-otlp")
//
//    testImplementation("org.springframework.boot:spring-boot-starter-data-r2dbc-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-micrometer-metrics-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
//    testImplementation("io.projectreactor:reactor-test")
//    testImplementation("org.springframework.cloud:spring-cloud-stream-test-binder")
//    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
//}
//
//dependencyManagement {
//    imports {
//        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
//    }
//}
//
//tasks.withType<Test> {
//    useJUnitPlatform()
//}
