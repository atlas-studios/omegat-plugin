# OmegaT plugin for Yandex.Translate (API v.1.5 - deprecated)

## About the plugin
This is a deprecated but still working (as of November 2021) version of Yandex.Translate connector for OmegaT originally developed as a plugin by Ilia Vinogradov, and later included into OmegaT itself. Since Yandex Cloud connector was developed for OmegaT, Yandex.Translate connector was removed. This plugin restores that functionality.

## Building
This plugin uses the same Gradle build system as OmegaT version 4.3.0 and later. It is necessary to be connected to the internet to compile this project in order to get dependencies resolved.

## Installation

The plugin's jar should be placed in `$HOME/.omegat/plugin` or `%APPDATA%\OmegaT\plugin`
depending on your operating system.

You'll need an API key which can be created [here](https://translate.yandex.com/developers/keys).

## Acknowledgments

OmegaT Dev Team for developing the best CAT tool and creating a superb OmegaT plugin skeleton which was used here.
