function Copy-UploadBackup {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )
    $ErrorActionPreference = 'Stop'
    if (-not (Test-Path -LiteralPath $Source -PathType Container)) {
        throw "Upload directory does not exist: $Source"
    }
    $sourceRoot = (Resolve-Path -LiteralPath $Source).Path.TrimEnd('\', '/')
    $destinationRoot = [IO.Path]::GetFullPath($Destination).TrimEnd('\', '/')
    if ($destinationRoot.Equals($sourceRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $destinationRoot.StartsWith($sourceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Backup destination must be outside the upload directory.'
    }
    New-Item -ItemType Directory -Force -Path $destinationRoot | Out-Null
    $manifest = @()
    foreach ($file in Get-ChildItem -LiteralPath $sourceRoot -File -Recurse -Force) {
        $relative = $file.FullName.Substring($sourceRoot.Length + 1)
        $target = Join-Path $destinationRoot $relative
        New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($target)) | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $target -Force -ErrorAction Stop
        $sourceHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        $targetHash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
        if ($sourceHash -ne $targetHash) { throw "Backup verification failed: $relative" }
        $manifest += [pscustomobject]@{ Path = $relative; Bytes = $file.Length; SHA256 = $targetHash }
    }
    return $manifest
}
