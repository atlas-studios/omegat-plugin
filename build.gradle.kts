plugins {
    java
    distribution
    application
    maven
    id("org.omegat.gradle") version "1.5.3"
    //id("com.github.johnrengelman.shadow") version "6.1.0"
}

version = "0.2.1"

project.setProperty("mainClassName", "com.atlasstudios.omegat")

repositories {
    mavenCentral()
}

omegat {
    version = "5.7.0"
    pluginClass = "com.atlasstudios.omegat.WebnovelTranslate"
}


distributions {
    main {
        contents {
            from(tasks["jar"], "README.md", "COPYING")
        }
    }
}


dependencies {
    packIntoJar("com.fasterxml.jackson.core:jackson-core:2.13.3")
    packIntoJar("com.fasterxml.jackson.core:jackson-databind:2.13.3")
    packIntoJar("com.fasterxml.jackson.core:jackson-annotations:2.13.3")
}