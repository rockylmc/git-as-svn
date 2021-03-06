import org.ajoberstar.grgit.Grgit
import org.asciidoctor.gradle.jvm.AbstractAsciidoctorTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

tasks.wrapper {
    gradleVersion = "6.8.1"
    distributionType = Wrapper.DistributionType.ALL
}

plugins {
    id("com.github.ben-manes.versions") version "0.36.0"
    id("com.github.hierynomus.license") version "0.15.0"
    id("org.ajoberstar.grgit") version "4.1.0"
    id("org.asciidoctor.jvm.convert") version "3.3.2"
    id("org.asciidoctor.jvm.pdf") version "3.3.2"
    id("org.asciidoctor.jvm.epub") version "3.3.2"
    id("org.jetbrains.kotlin.jvm") version "1.4.30"
    idea
    application
}

version = "1.30.1"

val javaVersion = JavaVersion.VERSION_11

idea {
    project.jdkName = javaVersion.name

    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

repositories {
    mavenCentral()
}

license {
    header = file("src/license/license_header.txt")
    exclude("**/*.json")
    exclude("**/*.yml")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = javaVersion.toString()
}

tasks.withType<Test> {
    useTestNG {
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            showStandardStreams = true
        }
    }
}

application {
    mainClass.set("svnserver.server.Main")
}

tasks.getByName<JavaExec>("run") {
    args = listOf("-c", "$projectDir/src/test/resources/config-local.yml")
}

dependencies {
    implementation("org.bouncycastle:bcpkix-jdk15on:1.68")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.10.0.202012080955-r")
    implementation("org.tmatesoft.svnkit:svnkit:1.10.1")
    implementation("org.yaml:snakeyaml:1.28")
    implementation("com.beust:jcommander:1.78")
    implementation("org.ini4j:ini4j:0.5.4")
    implementation("org.mapdb:mapdb:3.0.8")
    implementation("com.unboundid:unboundid-ldapsdk:5.1.3")
    implementation("org.eclipse.jetty:jetty-servlet:9.4.36.v20210114")
    implementation("org.gitlab:java-gitlab-api:4.1.1")
    implementation("org.bitbucket.b_c:jose4j:0.7.6")
    implementation("com.github.zeripath:java-gitea-api:1.7.4")

    val gitLfsJava = "0.17.0"
    implementation("ru.bozaro.gitlfs:gitlfs-pointer:$gitLfsJava")
    implementation("ru.bozaro.gitlfs:gitlfs-client:$gitLfsJava")
    implementation("ru.bozaro.gitlfs:gitlfs-server:$gitLfsJava")

    implementation("com.google.oauth-client:google-oauth-client:1.31.4")
    implementation("com.google.http-client:google-http-client-jackson2:1.38.1")
    implementation("org.slf4j:slf4j-api:1.7.30")

    runtimeOnly("org.apache.logging.log4j:log4j-slf4j18-impl:2.14.0")

    testImplementation("org.testcontainers:testcontainers:1.15.2")
    testImplementation("org.testng:testng:7.3.0")

    constraints {
        implementation("org.apache.httpcomponents:httpclient") {
            version {
                require("4.5.12")
                because("https://issues.apache.org/jira/browse/HTTPCLIENT-2047")
            }
        }
    }
}

tasks.jar {
    archiveFileName.set("${project.name}.jar")
    manifest {
        attributes(
            "Main-Class" to "svnserver.server.Main",
            "Class-Path" to createLauncherClassPath()
        )
    }
}

val compileDocs by tasks.registering(Copy::class) {
    group = "documentation"
    dependsOn(tasks.asciidoctor, tasks.asciidoctorEpub, tasks.asciidoctorPdf)

    from("$buildDir/docs/asciidoc") {
        into("htmlsingle")
    }
    from("$buildDir/docs/asciidocEpub")
    from("$buildDir/docs/asciidocPdf")
    from("$projectDir") {
        include("*.adoc", "LICENSE")
    }
    into(file("$buildDir/doc"))
}

asciidoctorj {
    modules {
        diagram.use()
    }
}

tasks.asciidoctor {
    configure()

    resources {
        from("src/docs/asciidoc") {
            include("examples/**")
            include("images/**")
        }
    }
}

tasks.asciidoctorEpub {
    configure()
    ebookFormats("epub3")
}

tasks.asciidoctorPdf {
    configure()
}

fun AbstractAsciidoctorTask.configure() {
    baseDirFollowsSourceDir()

    val commitDateTime = getCommitDateTime()
    attributes(
        mapOf(
            "source-highlighter" to "coderay",
            "docdate" to commitDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE),
            "doctime" to commitDateTime.format(DateTimeFormatter.ISO_LOCAL_TIME)
        )
    )
}

distributions {
    main {
        contents {
            into("doc") {
                from(compileDocs)
            }
            into("tools") {
                from("$projectDir/tools")
            }
        }
    }
}

tasks.processResources {
    from(sourceSets.main.get().resources.srcDirs) {
        include("**/VersionInfo.properties")

        expand(
            mapOf(
                "revision" to Grgit.open(mapOf("dir" to projectDir)).head().id,
                "tag" to (System.getenv("GITHUB_REF")?.substringAfter("refs/tags/") ?: System.getenv("TRAVIS_TAG") ?: "")
            )
        )
    }
}

val debianControl by tasks.registering(Copy::class) {
    from("$projectDir/src/main/deb") {
        exclude("**/changelog")
    }
    from("$projectDir/src/main/deb") {
        include("**/changelog")

        expand(
            mapOf(
                "version" to project.version,
                "date" to DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.US).format(getCommitDateTime())
            )
        )
    }
    into(file("$buildDir/debPackage/package"))
}

val compileDeb by tasks.registering(Exec::class) {
    dependsOn(tasks.installDist, debianControl)

    workingDir = file("$buildDir/debPackage/package")
    executable = "dpkg-buildpackage"
    args("-uc", "-us")
}

val distDeb by tasks.registering(Copy::class) {
    group = "distribution"
    dependsOn(compileDeb)

    from("$buildDir/debPackage") {
        include("*.deb")
    }
    into("$buildDir/distributions/debian_debian")
}

tasks.assembleDist {
    dependsOn(distDeb)
}

tasks.distZip {
    archiveFileName.set("${project.name}_${project.version}.zip")
}

tasks.distTar {
    archiveFileName.set("${project.name}_${project.version}.tbz2")
    compression = Compression.BZIP2
}

fun createLauncherClassPath(): String {
    val projectArtifacts = configurations.archives.get().artifacts.map { it.file }
    val fullArtifacts = configurations.archives.get().artifacts.map { it.file } + configurations.runtimeClasspath.get().files
    val vendorJars = fullArtifacts.minus(projectArtifacts).map { it.name }
    return vendorJars.joinToString(" ")
}

fun getCommitDateTime(): ZonedDateTime {
    return Grgit.open(mapOf("dir" to projectDir)).head().dateTime
}
