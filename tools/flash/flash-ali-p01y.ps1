[CmdletBinding()]
param(
    [string]$ImagePath = (Join-Path $PSScriptRoot "system.img")
)

$ErrorActionPreference = "Stop"

$ExpectedSha256 = "f40c224453a85f09a811cc365dd291db6def72d659d5d764f0dba759d35eea89"
$ExpectedImageBytes = [Int64]2052186112
$ExpectedSystemPartitionBytes = [Int64]2415919104
$ExpectedDeviceIds = @("P01Y", "P01Y_2")

function Stop-Flash([string]$Message) {
    throw $Message
}

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)][string]$Executable,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    # Intel's legacy fastboot writes normal getvar responses to STDERR.
    # With the script-wide ErrorActionPreference='Stop', PowerShell 5.1 can
    # turn the first (bootloader) line into a terminating error before the
    # remaining variables are collected. Temporarily use Continue while
    # invoking native ADB/Fastboot, while still preserving the real exit code.
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $raw = @(& $Executable @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $text = ($raw | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine

    return [pscustomobject]@{
        ExitCode = $exitCode
        Text = $text.Trim()
    }
}

function Invoke-Checked {
    param(
        [string]$Executable,
        [string[]]$Arguments,
        [string]$Action
    )

    $result = Invoke-Native -Executable $Executable -Arguments $Arguments
    if ($result.ExitCode -ne 0) {
        Stop-Flash "$Action failed (exit $($result.ExitCode)). $($result.Text)"
    }
    return $result.Text
}

function Get-AdbProperty {
    param([string]$Serial, [string]$Name)
    $value = Invoke-Checked -Executable $script:Adb -Arguments @("-s", $Serial, "shell", "getprop", $Name) -Action "Read $Name"
    return $value.Trim()
}

try {
    Write-Host ""
    Write-Host "ALI Car System - ASUS P01Y / WW Android 5.0.2" -ForegroundColor Cyan
    Write-Host "Safe system-partition flash utility"
    Write-Host ""

    $adbCommand = Get-Command "adb" -ErrorAction SilentlyContinue
    $fastbootCommand = Get-Command "fastboot" -ErrorAction SilentlyContinue
    if (-not $adbCommand -or -not $fastbootCommand) {
        Stop-Flash "ADB and Fastboot must both be installed and available in PATH. Install official Android SDK Platform-Tools."
    }
    $script:Adb = $adbCommand.Source
    $script:Fastboot = $fastbootCommand.Source

    if (-not (Test-Path -LiteralPath $ImagePath -PathType Leaf)) {
        Stop-Flash "system.img was not found. Put system.img in the same folder as this script or pass -ImagePath."
    }
    $image = Get-Item -LiteralPath $ImagePath
    if ($image.Length -ne $ExpectedImageBytes) {
        Stop-Flash "Image size mismatch. Expected $ExpectedImageBytes bytes; found $($image.Length). Do not flash a different build."
    }

    $hash = (Get-FileHash -LiteralPath $image.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $ExpectedSha256) {
        Stop-Flash "SHA-256 mismatch. Expected $ExpectedSha256 but found $hash. The file may be incomplete or different."
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
        Stop-Flash "The file does not contain the expected ext4 superblock magic at offset 1080."
    }

    Write-Host "[1/6] Image integrity: PASS" -ForegroundColor Green
    Write-Host "      Size: $($image.Length) bytes"
    Write-Host "      SHA-256: $hash"

    $adbDevices = Invoke-Checked -Executable $script:Adb -Arguments @("devices") -Action "List ADB devices"
    $online = @()
    foreach ($line in ($adbDevices -split "\r?\n")) {
        if ($line -match "^\s*(\S+)\s+device\s*$") {
            $online += $Matches[1]
        }
    }
    if ($online.Count -ne 1) {
        Stop-Flash "Expected exactly one authorized ADB device, found $($online.Count). Check USB debugging and disconnect other Android devices."
    }
    $adbSerial = $online[0]

    $deviceId = Get-AdbProperty -Serial $adbSerial -Name "ro.product.device"
    $fingerprint = Get-AdbProperty -Serial $adbSerial -Name "ro.build.fingerprint"
    $displayId = Get-AdbProperty -Serial $adbSerial -Name "ro.build.display.id"
    $sdk = Get-AdbProperty -Serial $adbSerial -Name "ro.build.version.sdk"
    $release = Get-AdbProperty -Serial $adbSerial -Name "ro.build.version.release"

    if ($ExpectedDeviceIds -notcontains $deviceId) {
        Stop-Flash "Device ID mismatch: '$deviceId'. This tool is only for P01Y / P01Y_2 (Z170CG)."
    }
    if ($sdk -ne "21" -or $release -ne "5.0.2") {
        Stop-Flash "Android build mismatch: SDK='$sdk', release='$release'. Expected Android 5.0.2 / API 21."
    }
    if (($fingerprint + " " + $displayId) -notmatch "WW_P01Y" -or ($fingerprint + " " + $displayId) -notmatch "12\.26\.4\.69") {
        Stop-Flash "The installed system does not identify itself as ASUS P01Y WW 12.26.4.69. No partition will be written."
    }

    Write-Host "[2/6] Device identity: PASS" -ForegroundColor Green
    Write-Host "      Device: $deviceId"
    Write-Host "      Build: $displayId"

    $mounts = Invoke-Checked -Executable $script:Adb -Arguments @("-s", $adbSerial, "shell", "cat", "/proc/mounts") -Action "Inspect /system mount"
    $systemMount = $null
    foreach ($line in ($mounts -split "\r?\n")) {
        if ($line -match "^\S+\s+/system\s+") {
            $systemMount = $line.Trim()
            break
        }
    }
    if (-not $systemMount) {
        Stop-Flash "Could not identify the live /system mount source. Verified-boot status cannot be safely inferred."
    }
    $systemSource = ($systemMount -split "\s+")[0]
    if ($systemSource -match "(^|/)dm-\d+$") {
        Stop-Flash "/system is mounted through '$systemSource' (device-mapper). This may indicate dm-verity. The stock boot image has not been audited for disabling it; do not flash this candidate."
    }
    if ($systemSource -notmatch "ImcPartID068$") {
        Stop-Flash "Unexpected /system source '$systemSource'. Expected the stock P01Y system partition ending in ImcPartID068. No write was attempted."
    }

    Write-Host "[3/6] System mount / verified-boot check: PASS" -ForegroundColor Green
    Write-Host "      /system source: $systemSource"

    Write-Host ""
    Write-Host "Rebooting the tablet to its P01Y Fastboot/Droidboot interface..."
    Invoke-Checked -Executable $script:Adb -Arguments @("-s", $adbSerial, "reboot", "fastboot") -Action "Reboot to Fastboot"

    $fastbootSerial = $null
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 1
        $devicesResult = Invoke-Native -Executable $script:Fastboot -Arguments @("devices")
        if ($devicesResult.ExitCode -eq 0) {
            $found = @()
            foreach ($line in ($devicesResult.Text -split "\r?\n")) {
                if ($line -match "^\s*(\S+)\s+fastboot\s*$") {
                    $found += $Matches[1]
                }
            }
            if ($found.Count -eq 1) {
                $fastbootSerial = $found[0]
                break
            }
            if ($found.Count -gt 1) {
                Stop-Flash "More than one Fastboot device is connected. Disconnect the others."
            }
        }
    }
    if (-not $fastbootSerial) {
        Stop-Flash "The tablet did not appear in Fastboot within 60 seconds. No flash command was sent."
    }

    Write-Host "[4/6] Fastboot connection: PASS" -ForegroundColor Green

    $vars = Invoke-Native -Executable $script:Fastboot -Arguments @("-s", $fastbootSerial, "getvar", "all")
    if ($vars.ExitCode -ne 0) {
        Stop-Flash "Could not read Fastboot variables. $($vars.Text)"
    }
    $varText = $vars.Text

    if ($varText -notmatch "(?im)^\s*(?:\(bootloader\)\s*)?product:\s*SF_3GR\s*$") {
        Stop-Flash "Fastboot product is not the expected Intel P01Y product SF_3GR. Actual data: $varText"
    }
    if ($varText -notmatch "(?im)^\s*(?:\(bootloader\)\s*)?unlocked:\s*(YES|TRUE|1)\s*$") {
        Stop-Flash "The bootloader is not confirmed unlocked. I will not unlock it automatically because ASUS unlock erases user data."
    }
    if ($varText -notmatch "(?im)^\s*(?:\(bootloader\)\s*)?system\s+par(?:t|i)tion\s+type:\s*ext4\s*$") {
        Stop-Flash "Fastboot did not confirm that the system partition is ext4. No write was attempted."
    }
    if ($varText -notmatch "(?im)^\s*(?:\(bootloader\)\s*)?system\s+par(?:t|i)tion\s+size:\s*(0x[0-9a-f]+)\s*$") {
        Stop-Flash "Fastboot did not report the system partition capacity. No write was attempted."
    }

    $sizeHex = $Matches[1]
    $reportedSystemBytes = [Convert]::ToInt64($sizeHex.Substring(2), 16)
    if ($reportedSystemBytes -ne $ExpectedSystemPartitionBytes) {
        Stop-Flash "System partition size mismatch. Expected 0x90000000 ($ExpectedSystemPartitionBytes bytes); Fastboot reports $sizeHex ($reportedSystemBytes bytes)."
    }
    if ($image.Length -ge $reportedSystemBytes) {
        Stop-Flash "The image is not smaller than the reported system partition. No write was attempted."
    }

    Write-Host "[5/6] Bootloader / partition checks: PASS" -ForegroundColor Green
    Write-Host "      Fastboot product: SF_3GR"
    Write-Host "      Bootloader: unlocked"
    Write-Host "      System type: ext4"
    Write-Host "      System capacity: $reportedSystemBytes bytes"
    Write-Host "      Candidate image: $($image.Length) bytes"

    Write-Host ""
    Write-Host "WARNING: this writes ONLY the system partition." -ForegroundColor Yellow
    Write-Host "It does NOT erase userdata/cache and does NOT flash boot, recovery, modem or bootloader."
    Write-Host "Keep the USB cable connected until Fastboot reports success."
    Write-Host ""
    $confirmation = Read-Host "To continue, type exactly FLASH ALI P01Y"
    if ($confirmation -cne "FLASH ALI P01Y") {
        Stop-Flash "Cancelled by user. No partition was written."
    }

    Write-Host "[6/6] Writing system partition..." -ForegroundColor Cyan
    $flash = Invoke-Native -Executable $script:Fastboot -Arguments @("-S", "256M", "-s", $fastbootSerial, "flash", "system", $image.FullName)
    Write-Host $flash.Text
    if ($flash.ExitCode -ne 0 -or $flash.Text -match "(?i)\bFAILED\b") {
        Stop-Flash "Fastboot reported a flash failure. Do not disconnect or try random commands. The tablet was left in Fastboot for recovery."
    }

    if ($flash.Text -notmatch "(?i)\bOKAY\b") {
        Stop-Flash "Fastboot did not return an OKAY confirmation. The tablet was left in Fastboot."
    }

    Write-Host "System partition write reported OKAY." -ForegroundColor Green
    Write-Host "Rebooting without wiping user data or cache..."
    $reboot = Invoke-Native -Executable $script:Fastboot -Arguments @("-s", $fastbootSerial, "reboot")
    Write-Host $reboot.Text
    if ($reboot.ExitCode -ne 0) {
        Write-Warning "The flash reported success but automatic reboot failed. Reboot from the device menu; do not flash again."
    }
    Write-Host ""
    Write-Host "Finished. First boot and physical functions still need on-device verification." -ForegroundColor Green
    exit 0
}
catch {
    Write-Host ""
    Write-Host "STOPPED SAFELY: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "No automatic bootloader unlock, userdata wipe, cache wipe, or secondary-partition flash was attempted."
    exit 1
}
