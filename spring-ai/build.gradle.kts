repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone/") }
}

dependencies {
    api(project(":core"))
    api(libs.spring.ai.model)
    implementation(libs.reactor.core)
    testImplementation(libs.reactor.test)
}