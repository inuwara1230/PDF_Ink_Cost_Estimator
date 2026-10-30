# build-full.ps1
# Builds the FULL (no trial) installer. Run from any folder; it works from the project root.
# Usage:  powershell -ExecutionPolicy Bypass -File .\build_full.ps1

# ======================= FILL THESE IN ===========================
$AppName     = "InkCostCalculatorFull"                 # shown in the Start menu and on the desktop shortcut
$AppVersion  = "1.0.1"                     # keep this >= the trial's version
$Vendor      = "InuwaraDI.com"                 # shown as Publisher in Installed apps
$UpgradeUuid = "f996f21a-645d-457b-ab93-e794407ceec8"      # same value in build-trial.ps1; NEVER change it
$IconFile    = "app_logo.ico"
$TrialFolder = "src\main\java\com\inuwara\pdf_ink_cost_estimator\trial"   # must NOT exist in the full build
# =================================================================

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# ---- 1. Safety checks -------------------------------------------
if ($UpgradeUuid -like "PASTE*" -or $Vendor -eq "YOUR NAME") {
    throw "Fill in `$Vendor and `$UpgradeUuid at the top of this script first."
}

$branch = git branch --show-current
if ($branch -notin @("main", "full-version")) {
    throw "You are on '$branch'. The full installer must be built from 'main' or 'full-version'."
}

# Only tracked files matter here; new untracked folders (installer_output etc.) are ignored
if (git status --porcelain --untracked-files=no) {
    throw "You have uncommitted changes. Commit them first so the installer matches your code."
}

if (Test-Path $TrialFolder) {
    throw "Trial code found at '$TrialFolder'. Do not ship this as the full version."
}

if (-not (Test-Path $IconFile)) { throw "Icon file '$IconFile' not found in the project folder." }
if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) { throw "jpackage not found. Check your JDK is installed and on PATH." }

Write-Host "Building FULL version $AppVersion from branch '$branch'..." -ForegroundColor Cyan

# ---- 2. Build the jar -------------------------------------------
.\mvnw clean package
if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }

$jars = @(Get-ChildItem "target" -Filter "*.jar")
if ($jars.Count -ne 1) { throw "Expected exactly one jar in target\, found $($jars.Count)." }
$jar = $jars[0].Name

# ---- 3. Put only the new jar in jpackage-input -----------------
New-Item -ItemType Directory -Force "jpackage-input" | Out-Null
Get-ChildItem "jpackage-input" | Remove-Item -Recurse -Force
Copy-Item "target\$jar" "jpackage-input\"

# ---- 4. Clean the output folder --------------------------------
$out = "installer_output\full"
New-Item -ItemType Directory -Force $out | Out-Null
Get-ChildItem $out | Remove-Item -Recurse -Force

# ---- 5. Build the MSI ------------------------------------------
jpackage --type msi `
  --name $AppName `
  --app-version $AppVersion `
  --vendor $Vendor `
  --input "jpackage-input" `
  --dest $out `
  --main-jar $jar `
  --icon $IconFile `
  --win-shortcut `
  --win-menu `
  --win-dir-chooser `
  --win-upgrade-uuid $UpgradeUuid
if ($LASTEXITCODE -ne 0) { throw "jpackage failed." }

# ---- 6. Label the file so it can't be mistaken for the trial ----
Get-ChildItem $out -Filter *.msi | Rename-Item -NewName { $_.Name -replace '\.msi$', '-Full.msi' }

Write-Host ""
Write-Host "Done. Full installer:" -ForegroundColor Green
Get-ChildItem $out -Filter *.msi | ForEach-Object { Write-Host "  $($_.FullName)" }