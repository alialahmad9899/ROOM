[CmdletBinding()]
param(
    [string]$ImagePath = (Join-Path $PSScriptRoot "system.img")
)

$ErrorActionPreference = "Stop"
$ExpectedSha256 = "f40c224453a85f09a811cc365dd291db6def72d659d5d764f0dba759d35eea89"
$ExpectedImageBytes = [Int64]2052186112
$ExpectedSystemPartitionBytes = [Int64]2415919104

function Stop-Recovery([string]$Message) {
    throw $Message
}

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)][string]$Executable,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    # Legacy Intel Fastboot writes normal bootloader responses to STDERR.
    # Do not treat those responses as PowerShell terminating errors.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $raw = @(& $Executable @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }

    $text = ($raw | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
    [pscustomobject]@{
        ExitCode = $exitCode
        Text = $text.Trim()
    }
}

try {
    Write-Host ""
    Write-Host "ALI Car System - P01Y Fastboot Recovery" -ForegroundColor Cyan
    Write-Host "System-only recovery. The tablet must remain in Fastboot/Droidboot."
    Write-Host ""

    $fastbootCommand = Get-Command "fastboot" -ErrorAction SilentlyContinue
    if (-not $fastbootCommand) {
        Stop-Recovery "fastboot.exe was not found in PATH. Install or expose Android/Intel Fastboot tools."
    }
    $fastboot = $fastbootCommand.Source

    if (-not (Test-Path -LiteralPath $ImagePath -PathType Leaf)) {
        Stop-Recovery "system.img was not found beside this script."
    }

    $image = Get-Item -LiteralPath $ImagePath
    if ($image.Length -ne $ExpectedImageBytes) {
        Stop-Recovery "Image size mismatch. Expected $ExpectedImageBytes bytes; found $($image.Length)."
    }

    $hash = (Get-FileHash -LiteralPath $image.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $ExpectedSha256) {
        Stop-Recovery "SHA-256 mismatch. Expected $ExpectedSha256 but found $hash. No write attempted."
    }

    $stream = [System.IO.File]::OpenRead($image.FullName)
    try {
        [void]$stream.Seek(1080, [System.IO.SeekOrigin]::Begin)
        $magic0 = $stream.ReadByte()
        $magic1 = $stream.ReadByte()
    }
    finally {
        $stream.Dispose()
    }
    if ($magic0 -ne 0x53 -or $magic1 -ne 0xEF) {
        Stop-Recovery "The image does not contain the expected ext4 superblock magic."
    }

    Write-Host "[1/4] Image integrity: PASS" -ForegroundColor Green
    Write-Host "      Size: $($image.Length) bytes"
    Write-Host "      SHA-256: $hash"

    $devices = Invoke-Native -Executable $fastboot -Arguments @("devices")
    if ($devices.ExitCode -ne 0) {
        Stop-Recovery "Could not list Fastboot devices. $($devices.Text)"
    }

    $serials = @()
    foreach ($line in ($devices.Text -split "\r?\n")) {
        if ($line -match "^\s*(\S+)\s+fastboot\s*$") {
            $serials += $Matches[1]
        }
    }
    if ($serials.Count -ne 1) {
        Stop-Recovery "Expected exactly one Fastboot device, found $($serials.Count). Leave the tablet in Fastboot and check the USB connection."
    }
    $serial = $serials[0]

    Write-Host "[2/4] Fastboot connection: PASS" -ForegroundColor Green
    Write-Host "      Device serial: $serial"

    $vars = Invoke-Native -Executable $fastboot -Arguments @("-s", $serial, "getvar", "all")
    if ($vars.ExitCode -ne 0) {
        Stop-Recovery "Could not read Fastboot variables. $($vars.Text)"
    }
    $varText = $vars.Text

    if ($varText -notmatch "(?im)^\s*(?:\(bootloader\)\s*)?product:\s*SF_3GR\s*$") {
        Stop-Recovery "Fastboot product mismatch. Expected SF_3GR. No write attempted."
    }
    if ($varText -notmatch "(?im)^\s*(?:\(bootloader\)\s*)?unlocked:\s*(YES|TRUE|1)\s*$") {
        Stop-Recovery "Bootloader is not confirmed unlocked. No write attempted."
    }
    if ($varText -notmatch "(?im)^\s*(?:\(bootloader\)\s*)?system\s+par(?:t|i)tion\s+type:\s*ext4\s*$") {
        Stop-Recovery "Fastboot did not confirm an ext4 system partition. No write attempted."
    }
    if ($varText -notmatch "(?im)^\s*(?:\(bootloader\)\s*)?system\s+par(?:t|i)tion\s+size:\s*(0x[0-9a-f]+)\s*$") {
        Stop-Recovery "Fastboot did not report the system partition size. No write attempted."
    }
    $sizeHex = $Matches[1]
    $reportedBytes = [Convert]::ToInt64($sizeHex.Substring(2), 16)
    if ($reportedBytes -ne $ExpectedSystemPartitionBytes) {
        Stop-Recovery "System size mismatch. Expected 0x90000000; Fastboot reports $sizeHex. No write attempted."
    }
    if ($image.Length -ge $reportedBytes) {
        Stop-Recovery "The image is not smaller than the system partition. No write attempted."
    }

    Write-Host "[3/4] Device and partition checks: PASS" -ForegroundColor Green
    Write-Host "      Product: SF_3GR"
    Write-Host "      Bootloader: unlocked"
    Write-Host "      System: ext4 / $reportedBytes bytes"
    Write-Host ""
    Write-Host "RECOVERY NOTICE:" -ForegroundColor Yellow
    Write-Host "The previous attempt erased system but failed during the first USB data transfer."
    Write-Host "This utility will rewrite ONLY system.img to the system partition."
    Write-Host "It will not touch userdata, cache, boot, recovery, modem or bootloader."
    Write-Host "The transfer chunks are reduced to 64 MiB to lower the size of each USB transfer."
    Write-Host "Use a direct USB 2.0 port and a known-good data cable. Do not use a hub."
    Write-Host ""

    $confirmation = Read-Host "To rewrite system, type exactly RESTORE ALI SYSTEM"
    if ($confirmation -cne "RESTORE ALI SYSTEM") {
        Stop-Recovery "Cancelled by user. No partition was written."
    }

    Write-Host ""
    Write-Host "[4/4] Rewriting system partition with 64 MiB transfer chunks..." -ForegroundColor Cyan
    $flash = Invoke-Native -Executable $fastboot -Arguments @("-S", "64M", "-s", $serial, "flash", "system", $image.FullName)
    Write-Host $flash.Text

    if ($flash.ExitCode -ne 0 -or $flash.Text -match "(?i)\bFAILED\b") {
        Stop-Recovery "Fastboot transfer/write failed again. Keep the tablet in Fastboot. Do not reboot or repeat blindly."
    }
    if ($flash.Text -notmatch "(?i)\bOKAY\b") {
        Stop-Recovery "Fastboot did not report OKAY. Keep the tablet in Fastboot."
    }

    Write-Host ""
    Write-Host "SYSTEM WRITE REPORTED OKAY." -ForegroundColor Green
    Write-Host "The tablet is being left in Fastboot for a final review."
    Write-Host "Do not run another flash. Save this output and confirm before rebooting."
    exit 0
}
catch {
    Write-Host ""
    Write-Host "RECOVERY STOPPED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
