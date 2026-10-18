$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectDirectory

if (-not (Test-Path -LiteralPath '.env' -PathType Leaf)) {
  throw 'Copie .env.example para .env e configure as senhas antes de iniciar.'
}

# O terminal pode ter sido aberto antes da instalação do Docker Desktop.
# O Docker precisa localizar os credential helpers pelo PATH do processo.
$dockerDirectories = @(
  (Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin'),
  (Join-Path $env:ProgramFiles 'Docker/Docker/resources/bin')
)
$dockerCommand = Get-Command docker.exe -ErrorAction SilentlyContinue
if ($dockerCommand) {
  $dockerDirectories = @((Split-Path -Parent $dockerCommand.Source)) + $dockerDirectories
}

$availableDirectories = $dockerDirectories |
  Where-Object { Test-Path -LiteralPath (Join-Path $_ 'docker.exe') -PathType Leaf } |
  Select-Object -Unique
if (-not $availableDirectories) {
  throw 'Docker Desktop não encontrado. Instale e inicie o Docker Desktop antes de continuar.'
}
$env:Path = (@($availableDirectories) -join ';') + ';' + $env:Path
$dockerCommand = Get-Command docker.exe -ErrorAction Stop

Write-Host 'Iniciando o projeto com o caminho do Docker Desktop configurado...'
& $dockerCommand.Source compose up -d --build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $dockerCommand.Source compose ps
exit $LASTEXITCODE
