param([switch]$Check)
$ErrorActionPreference='Stop'
$workspaceRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$scope=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'java-scope.json') -Raw | ConvertFrom-Json
$formatterJar=Join-Path ([Environment]::GetFolderPath('UserProfile')) ".m2/repository/com/google/googlejavaformat/google-java-format/$($scope.version)/google-java-format-$($scope.version)-all-deps.jar"
if(!(Test-Path -LiteralPath $formatterJar)){
    & mvn.cmd -q dependency:get "-Dartifact=com.google.googlejavaformat:google-java-format:$($scope.version):jar:all-deps"
    if($LASTEXITCODE -ne 0){throw 'Pinned Java formatter download failed'}
}
$arguments=@('-jar',$formatterJar)
if($Check){$arguments+=@('--dry-run','--set-exit-if-changed')}else{$arguments+='--replace'}
foreach($relative in $scope.files){
    $absolute=[IO.Path]::GetFullPath((Join-Path $workspaceRoot $relative))
    if(!$absolute.StartsWith($workspaceRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Java scope escaped workspace'}
    $arguments+=$absolute
}
& java @arguments
if($LASTEXITCODE -ne 0){throw 'Scoped Java formatting check failed'}
Write-Output "google-java-format $($scope.version): $($scope.files.Count) scoped classes"
