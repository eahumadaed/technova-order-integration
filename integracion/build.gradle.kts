plugins { java; application }
repositories { mavenCentral() }
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
dependencies {
 implementation("org.apache.activemq:artemis-jakarta-client:2.40.0")
 implementation("com.google.code.gson:gson:2.11.0")
 testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
 testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
application { mainClass = "cl.technova.Main" }
tasks.test { useJUnitPlatform() }
