$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

if (-not $IsWindows -and $PSVersionTable.PSEdition -eq 'Core') {
    throw 'Windows packages must be built on Windows.'
}

$packageType = if ($args.Count -gt 0) { $args[0] } else { 'app-image' }
if ($packageType -notin @('app-image', 'exe', 'msi')) {
    throw 'Expected app-image, exe, or msi.'
}

& .\gradlew.bat clean test installDist
if ($LASTEXITCODE -ne 0) { throw 'Gradle build or tests failed.' }

New-Item -ItemType Directory -Path 'build/native' -Force | Out-Null
& jpackage --type $packageType `
    --name AnimeEpisodeRandomizer `
    --app-version 1.0.0 `
    --description 'Choose a random anime episode from your saved library' `
    --input 'build/install/AnimeEpisodeRandomizer/lib' `
    --main-jar 'AnimeEpisodeRandomizer-1.0.0.jar' `
    --main-class 'dev.atomixsoft.randomizer.App' `
    --icon 'packaging/icon.ico' `
    --dest 'build/native' `
    --add-modules 'java.desktop,java.net.http,jdk.crypto.ec,jdk.charsets'
if ($LASTEXITCODE -ne 0) { throw 'Native packaging failed.' }
Write-Output "Created $packageType output in build/native"
