plugins {
	java
	alias(libs.plugins.spring.boot)
	alias(libs.plugins.spring.dependency.management)
}

group = "com.devpulse"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(libs.spring.boot.starter.actuator)
	implementation(libs.spring.boot.starter.data.jpa)
	implementation(libs.spring.boot.starter.data.redis)
	implementation(libs.spring.boot.starter.flyway)
	implementation(libs.spring.boot.starter.security)
	implementation(libs.spring.boot.starter.validation)
	implementation(libs.spring.boot.starter.webmvc)
	implementation(libs.flyway.database.postgresql)
	implementation(libs.jjwt.api)
	compileOnly(libs.lombok)
	runtimeOnly(libs.jjwt.impl)
	runtimeOnly(libs.jjwt.jackson)
	runtimeOnly(libs.micrometer.registry.prometheus)
	runtimeOnly(libs.postgresql)
	annotationProcessor(libs.lombok)
	testImplementation(libs.spring.boot.starter.data.jpa.test)
	testImplementation(libs.spring.boot.starter.flyway.test)
	testImplementation(libs.spring.boot.starter.security.test)
	testImplementation(libs.spring.boot.starter.webmvc.test)
	testImplementation(libs.spring.boot.testcontainers)
	testImplementation(libs.testcontainers.junit.jupiter)
	testImplementation(libs.testcontainers.postgresql)
	testCompileOnly(libs.lombok)
	testRuntimeOnly(libs.junit.platform.launcher)
	testAnnotationProcessor(libs.lombok)
}

// Only the executable jar, so the Dockerfile's build/libs/*.jar matches one file.
tasks.jar {
	enabled = false
}

tasks.withType<Test> {
	useJUnitPlatform()
}
