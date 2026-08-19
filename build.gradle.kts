plugins {
    id("java")
    kotlin("jvm")
    `maven-publish`
    signing
}

group = "xyz.baaad.machikado"
version = "3.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(26)
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        register<MavenPublication>("java") {
            artifactId = "java"
            group = "xyz.baaad.machikado"
            version = version
            from(components["java"])
            pom {
                name.set("java")
                description.set("machikado mazoku verifier like ZygiskNext")
                url.set("https://github.com/VeryBaaad/machikado-java")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://github.com/VeryBaaad/machikado-java/blob/master/LICENSE")
                    }
                }
                developers {
                    developer {
                        name.set("baaad")
                        url.set("https://baaad.xyz")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/VeryBaaad/machikado-java.git")
                    url.set("https://github.com/VeryBaaad/machikado-java")
                }
            }
        }
    }
    repositories {
        maven {
            name = "ossrh"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials(PasswordCredentials::class)
        }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/VeryBaaad/machikado-java")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

signing {
    val signingKey = findProperty("signingKey") as String?
    val signingPassword = findProperty("signingPassword") as String?
    if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}
