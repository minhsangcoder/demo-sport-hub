[CmdletBinding()]
param([switch]$StopDemoServices,[switch]$UseExternalTestDatabase,[int]$TestPort=15439,[switch]$TestsOnly,[switch]$SkipTests)
$ErrorActionPreference='Stop'
if($TestsOnly -and $SkipTests){throw 'TestsOnly and SkipTests are mutually exclusive.'}
$demoRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if($StopDemoServices){
 $demoJarPaths=@('identity-service','facility-service','schedule-service','booking-service','payment-service','transfer-service','api-gateway') | ForEach-Object {Join-Path $demoRoot "backend/$_/target/$_-0.0.1-SNAPSHOT.jar"}
 $demoProcesses=Get-CimInstance Win32_Process -Filter "name='java.exe'" | Where-Object { $demoCommandLine=$_.CommandLine; $demoCommandLine -and @($demoJarPaths | Where-Object {$demoCommandLine.Contains($_)}).Count -gt 0 }
 foreach($demoProcess in $demoProcesses){Stop-Process -Id $demoProcess.ProcessId -Force -ErrorAction SilentlyContinue}
}
if($UseExternalTestDatabase){
 if(!$env:SPORTHUB_TEST_DB_USER -or !$env:SPORTHUB_TEST_DB_PASSWORD){throw 'Set SPORTHUB_TEST_DB_USER and SPORTHUB_TEST_DB_PASSWORD for the dedicated test container.'}
 foreach($demoTestService in 'IDENTITY','FACILITY','SCHEDULE','BOOKING','PAYMENT','TRANSFER'){
  [Environment]::SetEnvironmentVariable("SPORTHUB_${demoTestService}_TEST_DB_URL","jdbc:postgresql://localhost:$TestPort/$($demoTestService.ToLower())_test",'Process')
 }
 $env:SPORTHUB_TEST_DB_URL=$env:SPORTHUB_IDENTITY_TEST_DB_URL
}
Push-Location (Join-Path $demoRoot 'backend')
try {if($TestsOnly){& mvn test -q}elseif($SkipTests){& mvn package -q -DskipTests}else{& mvn package -q};if($LASTEXITCODE -ne 0){throw "Backend build/tests failed: $LASTEXITCODE"}}
finally {Pop-Location}
