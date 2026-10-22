param([string]$Name='backend-finance-fixture')
$ErrorActionPreference='Stop'
if($Name -notmatch '^[a-z0-9-]+$'){throw 'Use a simple fixture directory name'}
$workspaceRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$fixtureRoot=[IO.Path]::GetFullPath((Join-Path $workspaceRoot "tmp/$Name"))
if(!$fixtureRoot.StartsWith((Join-Path $workspaceRoot 'tmp')+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Fixture must stay inside workspace tmp'}
New-Item -ItemType Directory -Path $fixtureRoot -Force | Out-Null
Push-Location $workspaceRoot
try {
    # Copy source/resources only. Keep compiler output independent from the editor and running services.
    $files=rg --files --hidden backend -g '!**/target/**' -g '!**/.env*' -g '!**/.git/**' -g '!**/*.md'
    if($LASTEXITCODE -ne 0){throw 'Backend source inventory failed'}
    foreach($file in $files){
        $relative=$file.Substring('backend'.Length).TrimStart([char[]]'\/')
        $destination=[IO.Path]::GetFullPath((Join-Path $fixtureRoot $relative))
        if(!$destination.StartsWith($fixtureRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Fixture path escaped its root'}
        New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($destination)) -Force | Out-Null
        Copy-Item -LiteralPath $file -Destination $destination -Force
    }
    Write-Output $fixtureRoot
} finally {Pop-Location}
