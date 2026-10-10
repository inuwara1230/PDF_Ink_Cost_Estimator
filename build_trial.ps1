# build-trial.ps1
# Builds the TRIAL installer. Keep this file on the trial-version branch.
# Usage:  powershell -ExecutionPolicy Bypass -File .\build_trial.ps1

# ======================= FILL THESE IN ===========================
$AppName     = "InkCostCalculatorTrial_1.0.0"                 # shown in the Start menu and on the desktop shortcut
$AppVersion  = "1.0.0"                     # keep this BELOW the full version's number
$Vendor      = "InuwaraDI.com"                 # shown as Publisher in Installed apps
$UpgradeUuid = "f996f21a-645d-457b-ab93-e794407ceec8"      # same value in build-full.ps1; never change it
$IconFile    = "app_logo.ico"
$TrialFile   = "src\main\java\com\inuwara\pdf_ink_cost_estimator\trial\TrialService.java"   # must EXIST in the trial build
# =================================================================

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# ---- 1. Safety checks -------------------------------------------
if ($UpgradeUuid -like "PASTE*" -or $Vendor -eq "YOUR NAME") {
    throw "Fill in `$Vendor and `$UpgradeUuid at the top of this script first."
}

$branch = git branch --show-current
if ($branch -ne "trial-version") {
    throw "You are on '$branch'. The trial installer must be built from 'trial-version'. Run: git checkout trial-version"
}

# Only tracked files matter here; new untracked folders (installer_output etc.) are ignored
if (git status --porcelain --untracked-files=no) {
    throw "You have uncommitted changes. Commit them first so the installer matches your code."
}

if (-not (Test-Path $TrialFile)) {
    throw "Trial code not found at '$TrialFile'. This would build a version with NO trial limit."
}

if (-not (Test-Path $IconFile)) { throw "Icon file '$IconFile' not found in the project folder." }
if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) { throw "jpackage not found. Check your JDK is installed and on PATH." }

Write-Host "Building TRIAL version $AppVersion from branch '$branch'..." -ForegroundColor Cyan

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
$out = "installer_output\trial"
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

# ---- 6. Label the file so it can't be mistaken for the full version
Get-ChildItem $out -Filter *.msi | Rename-Item -NewName { $_.Name -replace '\.msi$', '-Trial.msi' }

Write-Host ""
Write-Host "Done. Trial installer:" -ForegroundColor Green
Get-ChildItem $out -Filter *.msi | ForEach-Object { Write-Host "  $($_.FullName)" }