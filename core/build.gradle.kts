dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.resilience4j.circuitbreaker)
    api(libs.resilience4j.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
}
