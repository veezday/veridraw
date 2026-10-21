import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    id("com.diffplug.spotless") version "8.10.4"
    id("org.springframework.boot") version "4.1.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

subprojects {
    group = "io.veridraw"
    version = "0.1.0-SNAPSHOT"

    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "io.spring.dependency-management")

    if (name != "shared") {
        apply(plugin = "org.springframework.boot")
    }

    repositories {
        mavenCentral()
    }

    spotless {
        java {
            importOrder()
            removeUnusedImports()
            palantirJavaFormat("2.102.0")
            formatAnnotations()
        }
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.2")
            mavenBom("org.testcontainers:testcontainers-bom:2.0.5")
        }
    }

    plugins.withId("org.springframework.boot") {
        tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
            archiveFileName.set("${project.name}.jar")
        }
        tasks.named<Jar>("jar") {
            enabled = false
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test> {
        jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xmx2g")
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }
}