plugins {
    java
    distribution
    application
    maven
    id("org.omegat.gradle") version "1.5.3"
}

version = "0.2.0"

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
