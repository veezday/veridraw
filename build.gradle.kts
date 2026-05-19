plugins {
    java
    id("com.diffplug.spotless") version "6.25.0"
    id("org.springframework.boot") version "3.3.5" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
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
