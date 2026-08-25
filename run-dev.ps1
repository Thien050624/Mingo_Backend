# Starts the Mingo backend for local development.
#
# Sets TEMP/TMP to a path with no spaces (no 8.3 short-name aliasing like "MYPC~1").
# On this machine, Java's Selector.open() internally creates an AF_UNIX loopback socket
# under %TEMP%, and that fails with "Invalid argument: connect" when the resolved path
# goes through the short-name alias -- which breaks embedded Tomcat startup entirely.
$env:TEMP = "$PSScriptRoot\.tmp"
$env:TMP = "$PSScriptRoot\.tmp"
New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null

docker compose -f "$PSScriptRoot\docker-compose.yml" up -d

mvn -f "$PSScriptRoot\pom.xml" spring-boot:run
