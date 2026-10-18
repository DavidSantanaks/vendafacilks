$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectDirectory
if (-not (Test-Path '.env')) { throw 'Copie .env.example para .env e configure as senhas.' }
foreach ($line in Get-Content '.env') {
  if ($line -match '^\s*([A-Z][A-Z0-9_]*)=(.*)$') {
    [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim(), 'Process')
  }
}
if (-not $env:JAVA_HOME -and (Test-Path '.tools/jdk')) {
  $env:JAVA_HOME = (Get-ChildItem '.tools/jdk' -Directory | Select-Object -First 1).FullName
}
$env:MAVEN_USER_HOME = Join-Path $projectDirectory '.local/m2'
$env:SPRING_PROFILES_ACTIVE = 'local'
& ./mvnw.cmd spring-boot:run
exit $LASTEXITCODE
