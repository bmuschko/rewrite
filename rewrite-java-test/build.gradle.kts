plugins {
    id("org.openrewrite.build.language-library")
}

dependencies {
    implementation("org.assertj:assertj-core:latest.release")
    implementation(project(":rewrite-java"))
    implementation(project(":rewrite-groovy"))
    implementation(project(":rewrite-test"))

    testImplementation("io.github.classgraph:classgraph:latest.release")
    testImplementation("org.junit-pioneer:junit-pioneer:2.0.0")
    testRuntimeOnly(project(":rewrite-java-21"))
    testRuntimeOnly("org.apache.hbase:hbase-shaded-client:2.4.11")
    testRuntimeOnly("com.google.guava:guava:latest.release")
    testRuntimeOnly("org.mapstruct:mapstruct:latest.release")
    testRuntimeOnly(project(":rewrite-yaml"))
    testImplementation(project(":rewrite-properties"))
    testImplementation(project(":rewrite-xml"))
}

tasks.withType<Javadoc>().configureEach {
    isFailOnError = false
    exclude("org/openrewrite/java/**")
}

tasks.named<JavaCompile>("compileTestJava") {
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()

    options.release.set(null as Int?) // remove `--release 8` set in `org.openrewrite.java-base`
}

tasks.withType<Test>().configureEach {
    systemProperty("junit.jupiter.extensions.autodetection.enabled", true)
}

configurations.all {
    if (isCanBeConsumed) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
    }
}
