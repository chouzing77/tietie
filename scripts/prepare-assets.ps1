param([string]$Source, [string]$Archive)
$ErrorActionPreference = 'Stop'
if ([bool]$Source -eq [bool]$Archive) { throw 'Choose exactly one: -Source or -Archive' }
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$assetPaths = @(
  '离线模型/ort.wasm.min.js', '离线模型/wasm-code.js', '离线模型/wasm-binary.js',
  '离线模型/model-u2.js', '离线模型/model-mobilenet.js',
  '参考字体/Huiwen-mincho.ttf', '参考字体/汇文明朝体-授权说明.txt'
)
$archiveHandle = $null
try {
  if ($Archive) { Add-Type -AssemblyName System.IO.Compression.FileSystem; $archiveHandle = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Archive).Path) }
  else { $sourceRoot = (Resolve-Path -LiteralPath $Source).Path }
  foreach ($relative in $assetPaths) {
    $target = [IO.Path]::GetFullPath((Join-Path $repoRoot ('web/' + $relative)))
    if (-not $target.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe destination' }
    New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($target)) -Force | Out-Null
    if ($archiveHandle) {
      $entry = $archiveHandle.GetEntry('workspace/zing77/记录工具_开发版/' + $relative)
      if (-not $entry) { throw ('Missing archive resource: ' + $relative) }
      [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
    } else { Copy-Item -LiteralPath (Join-Path $sourceRoot $relative) -Destination $target -Force }
    Write-Output ('Prepared ' + $relative)
  }
} finally { if ($archiveHandle) { $archiveHandle.Dispose() } }
