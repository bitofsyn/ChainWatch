plugins {
    id("java")
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.chainwatch"
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
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.web3j:core:4.12.2")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // 테스트는 기본 설정의 Redis(localhost:6379)에 접속한다. 로컬에서 앱 스택이 떠 있을 때
    // 테스트 메시지가 실제 최신 피드 캐시에 섞이지 않도록 테스트 전용 키를 사용한다.
    systemProperty("chainwatch.feed.recent-transactions-key", "chainwatch:test:feed:transactions")
    systemProperty("chainwatch.feed.recent-events-key", "chainwatch:test:feed:events")
    // JWT를 켠 테스트도 초기 계정으로 로그인한다. 운영 코드는 JWT 활성 상태에서 개발 기본값
    // ({noop}chainwatch)으로 계정을 만들지 않으므로, 같은 비밀번호("chainwatch")의 BCrypt 해시를 준다.
    val testPasswordHash = "{bcrypt}\$2y\$10\$fsP/xKJwwKsAFRq3LVW5J.1m6XPeQUHSRLkwaIa9ScuKnuDsW38Y6"
    systemProperty("chainwatch.security.admin-password", testPasswordHash)
    systemProperty("chainwatch.security.analyst-password", testPasswordHash)
}
