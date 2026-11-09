plugins { java; application }
repositories { mavenCentral() }
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
dependencies {
 implementation("org.apache.activemq:artemis-jakarta-client:2.40.0")
 implementation("com.google.code.gson:gson:2.11.0")
 testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
 testImplementation("org.apache.activemq:artemis-server:2.40.0")
 testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
application { mainClass = "cl.technova.Main" }
tasks.test { useJUnitPlatform { excludeTags("integration") } }
tasks.register<Test>("integrationTest") {
 description = "Runs integration tests against an embedded Artemis broker and a local SOAP server"
 group = "verification"
 testClassesDirs = sourceSets.test.get().output.classesDirs
 classpath = sourceSets.test.get().runtimeClasspath
 useJUnitPlatform { includeTags("integration") }
 shouldRunAfter(tasks.test)
}
