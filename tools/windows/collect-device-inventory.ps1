$ErrorActionPreference = "Stop"

function Invoke-AdbText([string]$Arguments) {
    $parts = $Arguments -split " "
    $output = & adb @parts 2>&1
    if ($LASTEXITCODE -ne 0) {
        return "[command failed: adb $Arguments]`r`n$($output -join "`r`n")"
    }
    return ($output -join "`r`n")
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb غير موجود. ثبّت Android Platform Tools وأعد المحاولة."
}

$devices = & adb devices
$online = @($devices | Where-Object { $_ -match "^[^\s]+\s+device$" })
if ($online.Count -ne 1) {
    throw "يجب توصيل جهاز واحد فقط بحالة device. الناتج: $($devices -join '; ')"
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outDir = Join-Path (Get-Location) "device-inventory"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$outFile = Join-Path $outDir "P01Y-inventory-$stamp.txt"

$sections = @(
    @{Title="DEVICE BUILD PROPERTIES"; Args="shell getprop"},
    @{Title="SYSTEM PACKAGES (with APK paths)"; Args="shell pm list packages -s -f"},
    @{Title="USER INSTALLED PACKAGES (with APK paths)"; Args="shell pm list packages -3 -f"},
    @{Title="DISABLED PACKAGES"; Args="shell pm list packages -d"},
    @{Title="STORAGE"; Args="shell df"},
    @{Title="BATTERY"; Args="shell dumpsys battery"},
    @{Title="MEMORY SUMMARY"; Args="shell dumpsys meminfo"},
    @{Title="RUNNING PROCESSES"; Args="shell dumpsys activity processes"}
)

$builder = New-Object System.Text.StringBuilder
[void]$builder.AppendLine("ASUS P01Y READ-ONLY INVENTORY")
[void]$builder.AppendLine("Captured: $(Get-Date -Format o)")
[void]$builder.AppendLine("This report is read-only. No packages were changed.")
[void]$builder.AppendLine("Device serial intentionally omitted.")
foreach ($section in $sections) {
    [void]$builder.AppendLine("`r`n===== $($section.Title) =====")
    $content = Invoke-AdbText $section.Args
    if ($section.Title -eq "DEVICE BUILD PROPERTIES") {
        $content = ($content -split "\r?\n" | Where-Object {
            $_ -match "\[(ro\.build\.|ro\.product\.|ro\.hardware|ro\.board\.platform|ro\.boot\.)"
        }) -join "`r`n"
    }
    [void]$builder.AppendLine($content)
}
[System.IO.File]::WriteAllText($outFile, $builder.ToString(), [System.Text.Encoding]::UTF8)
Write-Host "تم إنشاء تقرير قراءة فقط:"
Write-Host $outFile
