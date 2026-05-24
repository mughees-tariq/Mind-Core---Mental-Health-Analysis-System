param(
    [Parameter(Position=0)]
    [string]$Command = "run"
)

$API_DIR  = "python-api"
$APP_DIR  = "mental-health-app"
$VENV_DIR = "$API_DIR\venv"
$PYTHON   = "$VENV_DIR\Scripts\python.exe"
$PIP      = "$VENV_DIR\Scripts\pip.exe"
$ROOT     = $PSScriptRoot

function Ensure-Venv {
    if (-not (Test-Path "$ROOT\$PYTHON")) {
        Write-Host ">>> venv not found. Creating..." -ForegroundColor Yellow
        $sysPython = Get-Command python -ErrorAction SilentlyContinue
        if (-not $sysPython) {
            Write-Host "ERROR: Python not found. Install Python 3.10+ from https://python.org" -ForegroundColor Red
            exit 1
        }
        & python -m venv "$ROOT\$VENV_DIR"
        # Bootstrap pip in case ensurepip is missing
        if (-not (Test-Path "$ROOT\$PIP")) {
            Write-Host ">>> pip missing from venv, bootstrapping..." -ForegroundColor Yellow
            & "$ROOT\$PYTHON" -m ensurepip --upgrade
            & "$ROOT\$PYTHON" -m pip install --upgrade pip --quiet
        }
        Write-Host ">>> venv created." -ForegroundColor Green
    }
}

function Ensure-Deps {
    Ensure-Venv
    Write-Host ">>> Checking Python dependencies..." -ForegroundColor Cyan

    # Check if torch is importable (key indicator that deps are installed)
    $torchCheck = & "$ROOT\$PYTHON" -c "import torch, transformers, flask" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host ">>> Missing dependencies. Installing (this may take a few minutes)..." -ForegroundColor Yellow

        # Install torch CPU-only first (smaller, portable, no CUDA needed)
        Write-Host ">>> Installing PyTorch (CPU)..." -ForegroundColor Cyan
        & "$ROOT\$PIP" install torch --index-url https://download.pytorch.org/whl/cpu --quiet

        # Install rest of requirements
        Write-Host ">>> Installing remaining requirements..." -ForegroundColor Cyan
        & "$ROOT\$PIP" install -r "$ROOT\$API_DIR\requirements.txt" --quiet

        Write-Host ">>> Dependencies installed." -ForegroundColor Green
    } else {
        Write-Host ">>> All dependencies OK." -ForegroundColor Green
    }
}

function Start-Api {
    Ensure-Deps
    Write-Host ">>> Starting Flask API..." -ForegroundColor Cyan
    $script:flask = Start-Process -FilePath "$ROOT\$PYTHON" `
        -ArgumentList "app.py" `
        -WorkingDirectory "$ROOT\$API_DIR" `
        -PassThru -WindowStyle Minimized
    Write-Host ">>> Flask API started (PID $($script:flask.Id))" -ForegroundColor Green
}

function Start-App {
    Write-Host ">>> Launching Java app..." -ForegroundColor Cyan
    Push-Location "$ROOT\$APP_DIR"
    mvn javafx:run -q
    Pop-Location
}

switch ($Command) {
    "run" {
        Start-Api
        Write-Host ">>> Waiting for Flask API to be ready..." -ForegroundColor Yellow
        $maxWait = 180
        $elapsed = 0
        $ready = $false
        while ($elapsed -lt $maxWait) {
            try {
                $response = Invoke-WebRequest -Uri "http://localhost:5000/health" -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
                $json = $response.Content | ConvertFrom-Json
                if ($json.status -eq "healthy" -and $json.model_loaded -eq $true) {
                    Write-Host ">>> Flask API ready! (took ${elapsed}s)" -ForegroundColor Green
                    $ready = $true
                    break
                }
            } catch {
                # Not up yet, keep waiting
            }
            Start-Sleep -Seconds 2
            $elapsed += 2
        }
        if (-not $ready) {
            Write-Host "ERROR: Flask API did not become ready after ${maxWait}s. Check python-api logs." -ForegroundColor Red
            exit 1
        }
        try {
            Start-App
        } finally {
            if ($script:flask -and -not $script:flask.HasExited) {
                Write-Host ">>> Stopping Flask API (PID $($script:flask.Id))..." -ForegroundColor Cyan
                $script:flask | Stop-Process -Force
                Write-Host ">>> Flask API stopped." -ForegroundColor Green
            }
        }
    }
    "api" {
        Ensure-Deps
        Write-Host ">>> Starting Flask API (foreground)..." -ForegroundColor Cyan
        & "$ROOT\$PYTHON" "$ROOT\$API_DIR\app.py"
    }
    "app" {
        Start-App
    }
    "build" {
        Write-Host ">>> Compiling Java app..." -ForegroundColor Cyan
        Push-Location "$ROOT\$APP_DIR"
        mvn compile -q
        Pop-Location
    }
    "install" {
        Ensure-Deps
        Write-Host ">>> Resolving Maven dependencies..." -ForegroundColor Cyan
        Push-Location "$ROOT\$APP_DIR"
        mvn dependency:resolve -q
        Pop-Location
        Write-Host ">>> All dependencies installed." -ForegroundColor Green
    }
    "stop" {
        Write-Host ">>> Stopping Flask on port 5000..." -ForegroundColor Cyan
        $pid5000 = (netstat -ano | Select-String ":5000 .*LISTENING") -replace '.*\s+(\d+)$','$1'
        if ($pid5000) {
            Stop-Process -Id $pid5000 -Force
            Write-Host ">>> Stopped PID $pid5000" -ForegroundColor Green
        } else {
            Write-Host ">>> Flask not running on port 5000" -ForegroundColor Yellow
        }
    }
    "help" {
        Write-Host ""
        Write-Host "  .\run.ps1 run      Start Flask API + Java app (auto-installs if needed)"  -ForegroundColor White
        Write-Host "  .\run.ps1 api      Start only the Flask API (auto-installs if needed)"    -ForegroundColor White
        Write-Host "  .\run.ps1 app      Start only the Java app"     -ForegroundColor White
        Write-Host "  .\run.ps1 build    Compile Java app"            -ForegroundColor White
        Write-Host "  .\run.ps1 install  Install all dependencies"    -ForegroundColor White
        Write-Host "  .\run.ps1 stop     Kill Flask API process"      -ForegroundColor White
        Write-Host ""
    }
    default {
        Write-Host "Unknown command '$Command'. Run '.\run.ps1 help' for usage." -ForegroundColor Red
    }
}