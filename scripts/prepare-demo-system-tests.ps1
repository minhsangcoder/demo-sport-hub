param([switch]$SkipBuild)
$ErrorActionPreference='Stop'
$workspaceRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$uiFixture=Join-Path $workspaceRoot 'tmp/demo-system-ui'
Push-Location $workspaceRoot
try {
    & './scripts/backend-test-fixture.ps1' -Name backend-demo-system
    New-Item -ItemType Directory -Path $uiFixture -Force | Out-Null
    foreach($directory in @('app','components','features','lib','public')) {
        Copy-Item -LiteralPath (Join-Path $workspaceRoot "frontend/$directory") -Destination $uiFixture -Recurse -Force
    }
    foreach($file in @('package.json','tsconfig.json','next-env.d.ts','next.config.mjs','components.json')) {
        $source=Join-Path $workspaceRoot "frontend/$file"
        if(Test-Path -LiteralPath $source){Copy-Item -LiteralPath $source -Destination $uiFixture -Force}
    }
    if(!(Test-Path -LiteralPath (Join-Path $uiFixture 'node_modules'))) {
        New-Item -ItemType Junction -Path (Join-Path $uiFixture 'node_modules') -Target (Join-Path $workspaceRoot 'frontend/node_modules') | Out-Null
    }
    # Restrict Tailwind scanning to this fixture, independently of the invoking shell's directory.
    $postcss=@'
import {fileURLToPath} from 'node:url'
export default {plugins:{'@tailwindcss/postcss':{base:fileURLToPath(new URL('.',import.meta.url))}}}
'@
    [IO.File]::WriteAllText((Join-Path $uiFixture 'postcss.config.mjs'),$postcss,[Text.UTF8Encoding]::new($false))
    if(!$SkipBuild) {
        mvn.cmd -q -f tmp/backend-demo-system/pom.xml -pl identity-service,facility-service,schedule-service,booking-service,payment-service,transfer-service,api-gateway -am -DskipTests package
        if($LASTEXITCODE -ne 0){throw 'Isolated backend artifact build failed'}
        Push-Location $uiFixture
        try {
            & node (Join-Path $workspaceRoot 'frontend/node_modules/next/dist/bin/next') build --webpack
            if($LASTEXITCODE -ne 0){throw 'Isolated frontend build failed'}
        } finally {Pop-Location}
    }
} finally {Pop-Location}
