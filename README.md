# OmegaT plugin for Custom MT engine

## About the plugin
This is built on the deprecated Yandex.Translate connector for 
OmegaT originally developed as a plugin by Ilia Vinogradov.

## Building
This plugin uses the same Gradle build system as OmegaT version 5.7.0 and later. It is necessary to be connected to the internet to compile this project in order to get dependencies resolved.

To compile, use the following command:
`./gradlew build`

## Installation

The plugin's jar should be placed in `$HOME/.omegat/plugin` or `%APPDATA%\OmegaT\plugin`
depending on your operating system.

## Acknowledgments

OmegaT Dev Team for developing the best CAT tool and creating a superb OmegaT plugin skeleton which was used here.
