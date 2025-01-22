// Build.gradle for creating or installing new instrumentation modules


// Global defaults - override here or in individual modules as needed.
buildscript {


    repositories {
    flatDir{
        dirs("template-lib")
    }
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
  }

  dependencies {
      classpath("com.newrelic.agent.java:gradle-verify-instrumentation-plugin:3.2")
      classpath("de.undercouch:gradle-download-task:5.0.0")
  }
}

plugins {
    id("java")
}


project.ext {
    extra.apply{
        set("group","com.newrelic.instrumentation.labs")
        set("javaAgentVersion","6.4.0")
        set("javaVersion",JavaVersion.VERSION_1_8)
    }

    // Aligned with minimum Java major version supported by latest Java Agent

}


apply(plugin = "java")
apply(plugin = "de.undercouch.download")

tasks.create<Copy>("extractJars") {

    from(zipTree(projectDir.path+"/libs/newrelic-agent-"+ext.get("javaAgentVersion")+".jar"))
    into(projectDir.path+"/libs")
}

tasks.create<Delete>("cleanUp") {
    delete(projectDir.path+"/libs/META-INF", projectDir.path+"/libs/com", projectDir.path+"/libs/mozilla")
    delete(projectDir.path+"/libs/LICENSE", projectDir.path+"/libs/Log4j-events.dtd", projectDir.path+"/libs/THIRD_PARTY_NOTICES.md")
    delete(fileTree(projectDir.path+"/libs") {
        include("**/*.xsd")
        include("**/*.xml")
        include("**/*.yml")
        include("**/*.properties")
    })
}

tasks.create<Exec>("checkForDependencies") {
    val javaAgentVersion: String by extra
//    environment("JAVAAGENTVERSION", javaAgentVersion)
    val rootProject = projectDir.path
    val cmdLine = rootProject+"/newrelic-dependencies.sh"
    workingDir(rootProject)
    commandLine(cmdLine)
}


tasks {

    register("buildIfNeeded") {
        dependsOn("checkForDependencies")
        dependsOn("jar")
        findByName("jar")?.mustRunAfter("checkForDependencies")
    }



    register("createModule") {
        dependsOn("checkForDependencies")
        description = "Generate project files for a new instrumentation module"
        group = "New Relic"
        doLast {

            val rootProject = projectDir.path


            var projectGroup = System.console().readLine("Instrumentation Module Group (default: " + extra["group"]  + ") (Hit return to use default):\n")
            var projectName = System.console().readLine("Instrumentation Module Name:\n")

            if (projectName == null) {
                throw Exception("Please specify a valid module name.")
            } else {
                projectName = projectName.trim()
            }

            if (projectGroup == null || projectGroup.trim() == "") {
                projectGroup = extra["group"] as String
            } else {
                projectGroup = projectGroup.trim()
            }

            val projectLibDir = file(rootProject+"/lib")

            val projectPath = file(rootProject + "/" +projectName)
            if (projectPath.exists()) {
                throw Exception(projectPath.path + " already exists.")
            }

            val projectJava = file(projectPath.path + "/src/main/java")
            val projectTest = file(projectPath.path +  "/src/test/java")
            mkdir(projectJava)
            mkdir(projectTest)

            val subProjectBuildFile = file(projectPath.path + "/build.gradle.kts")

            val settings = file("settings.gradle")
            settings.appendText("include "+projectName+"\n")
            println("Created module in "+projectPath.path+".")
        }
    }
}



subprojects {
  repositories {
    mavenLocal()
    mavenCentral()
  }

  apply(plugin = "java")
  apply(plugin = "eclipse")
  apply(plugin = "idea")
  apply(plugin = "com.newrelic.gradle-verify-instrumentation-plugin")

  val sourceCompatibility = extra["javaVersion"]
  val targetCompatibility = extra["javaVersion"]

  dependencies {
//    testImplementation(
//        fileTree(dir("../lib") {
//            include("*.jar")
//        })
//    )
    testImplementation("org.nanohttpd:nanohttpd:2.3.1")
    testImplementation("com.newrelic.agent.java:newrelic-agent:" + extra["javaAgentVersion"] as String)
  }

    tasks {
        create<Copy>("install") {
            dependsOn("buildIfNeeded")
            description ="Copies compiled jar to the NEW_RELIC_EXTENSIONS_DIR."
            group = "New Relic"

            val extDir = System.getenv("NEW_RELIC_EXTENSIONS_DIR") ?: " "

            from(jar)
            into(extDir)

            doFirst  {
                var extDir = System.getenv("NEW_RELIC_EXTENSIONS_DIR")
                if (extDir == null) {
                    throw Exception("Must set NEW_RELIC_EXTENSIONS_DIR.")
                }

                if (extDir.startsWith("~" + File.separator)) {
                    extDir = System.getProperty("user.home") + extDir.substring(1);
                }

                if (!file(extDir).isDirectory) {
                    throw Exception(extDir + "NEW_RELIC_EXTENSIONS_DIR, set as '" + extDir + "'is not a valid directory.")
                }
            }
        }


    }

  tasks.named<JavaCompile>("compileJava") {
      doFirst {
          tasks.findByName("checkForDependencies")
      }
  }


}
