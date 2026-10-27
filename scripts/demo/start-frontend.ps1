[CmdletBinding()]
param([switch]$Rebuild,[ValidateRange(1024,65535)][int]$Port=3001)
$ErrorActionPreference='Stop'
$frontendRoot=(Resolve-Path (Join-Path $PSScriptRoot '../../frontend')).Path
$frontendServer=Join-Path $frontendRoot '.next/standalone/server.js'
Push-Location $frontendRoot
try {
 $frontendOwnedPids=@(Get-CimInstance Win32_Process -Filter "name='node.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains($frontendServer)} | Select-Object -ExpandProperty ProcessId)
 if($Rebuild){
  if($frontendOwnedPids.Count){Stop-Process -Id $frontendOwnedPids -Force -ErrorAction SilentlyContinue}
  & pnpm build --webpack
  if($LASTEXITCODE -ne 0){throw 'Frontend production build failed.'}
 }
 $frontendBuild=Join-Path $frontendRoot '.next/BUILD_ID'
 $frontendStandalone=Join-Path $frontendRoot '.next/standalone'
 if(!(Test-Path -LiteralPath $frontendBuild) -or !(Test-Path -LiteralPath (Join-Path $frontendStandalone 'server.js'))){throw 'Build first: pwsh scripts/demo/start-frontend.ps1 -Rebuild'}
 $frontendInputs=@('app','components','features','lib','public') | ForEach-Object {Get-ChildItem -LiteralPath (Join-Path $frontendRoot $_) -Recurse -File}
 $frontendInputs+=Get-ChildItem -LiteralPath $frontendRoot -File | Where-Object {$_.Name -match '^(next\.config\.|postcss\.config\.|tailwind\.config\.|tsconfig\.json$|package\.json$|pnpm-lock\.yaml$|\.env)'}
 $frontendBuildTime=(Get-Item -LiteralPath $frontendBuild).LastWriteTimeUtc
 if($frontendInputs | Where-Object {$_.LastWriteTimeUtc -gt $frontendBuildTime} | Select-Object -First 1){throw 'Frontend source is newer than the build. Run: pwsh scripts/demo/start-frontend.ps1 -Rebuild'}
 if(!$Rebuild -and $frontendOwnedPids.Count){Stop-Process -Id $frontendOwnedPids -Force -ErrorAction SilentlyContinue}
 Copy-Item -LiteralPath (Join-Path $frontendRoot '.next/static') -Destination (Join-Path $frontendStandalone '.next') -Recurse -Force
 Copy-Item -LiteralPath (Join-Path $frontendRoot 'public') -Destination $frontendStandalone -Recurse -Force
 $env:GATEWAY_URL='http://localhost:18080'
 $env:PORT=[string]$Port
 $env:HOSTNAME='127.0.0.1'
 & node $frontendServer
 exit $LASTEXITCODE
} finally {Pop-Location}
