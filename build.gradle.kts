import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.izzel.taboolib.gradle.Basic
import io.izzel.taboolib.gradle.BukkitNMS
import io.izzel.taboolib.gradle.BukkitFakeOp
import io.izzel.taboolib.gradle.BukkitNMSEntityAI
import io.izzel.taboolib.gradle.BukkitNMSItemTag
import io.izzel.taboolib.gradle.BukkitNMSUtil
import io.izzel.taboolib.gradle.BukkitNavigation
import io.izzel.taboolib.gradle.BukkitUI
import io.izzel.taboolib.gradle.BukkitUtil
import io.izzel.taboolib.gradle.CommandHelper
import io.izzel.taboolib.gradle.MinecraftChat
import io.izzel.taboolib.gradle.Bukkit
import io.izzel.taboolib.gradle.JavaScript
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    application
    id("io.izzel.taboolib") version "2.0.36"
    kotlin("jvm") version "2.3.20"
}

application {
    mainClass.set("moe.tachyon.ctf.server.MainKt")
}

taboolib {
    env {
        install(Basic)
        install(BukkitNMS)
        install(BukkitFakeOp)
        install(BukkitNMSEntityAI)
        install(BukkitNMSItemTag)
        install(BukkitNMSUtil)
        install(BukkitNavigation)
        install(BukkitUI)
        install(BukkitUtil)
        install(CommandHelper)
        install(MinecraftChat)
        install(Bukkit)
        install(JavaScript)
    }
    description {
        name = "Minecraft-CTF-Plugin"
        contributors {
            name("CyanTachyon")
        }
    }
    version { taboolib = "6.2.4-6b473c4" }
    relocate("top.maplex.arim", "moe.tachyon.ctf.arim")

}

repositories {
    mavenCentral()
//    maven {
//        url = uri("https://nexus.maplex.top/repository/maven-public/")
//        isAllowInsecureProtocol = true
//    }
//    maven {
//        name = "papermc"
//        url = uri("https://repo.papermc.io/repository/maven-public/")
//    }
}

dependencies {
    compileOnly("ink.ptms.core:v12101:12101:mapped")
    compileOnly("ink.ptms.core:v12101:12101:universal")
//    compileOnly("net.kyori:adventure-api:4.26.1")
    taboo(kotlin("stdlib"))
//    taboo("top.maplex.arim:Arim:1.3.12")
    compileOnly(fileTree("libs"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "moe.tachyon.ctf.server.MainKt"
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xcontext-parameters"))
}