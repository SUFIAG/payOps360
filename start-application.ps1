# PayOps360 - Quick Start Script
# This script helps you quickly start the application

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  PayOps360 - Quick Start Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if database exists
Write-Host "[ ] Checking PostgreSQL connection..." -ForegroundColor Yellow
$dbCheck = psql -U postgres -d postgres -c "SELECT 1 FROM pg_database WHERE datname='payops360'" -t 2>&1

if ($dbCheck -match "1") {
    Write-Host "[✓] Database 'payops360' exists" -ForegroundColor Green
} else {
    Write-Host "[!] Database 'payops360' does not exist" -ForegroundColor Red
    Write-Host ""
    Write-Host "Creating database..." -ForegroundColor Yellow

    $createDb = @"
CREATE DATABASE payops360
WITH
OWNER = postgres
ENCODING = 'UTF8'
TEMPLATE = template0;
"@

    $createDb | psql -U postgres -d postgres

    if ($LASTEXITCODE -eq 0) {
        Write-Host "[✓] Database created successfully" -ForegroundColor Green
    } else {
        Write-Host "[✗] Failed to create database" -ForegroundColor Red
        Write-Host "Please create it manually:" -ForegroundColor Yellow
        Write-Host "  psql -U postgres -c ""CREATE DATABASE payops360;""" -ForegroundColor White
        exit 1
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Starting Backend Server" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Start backend in new window
$backendPath = "C:\Users\sufyan.abdulghani\Downloads\MVP\payOps360\payops360-backend"
Write-Host "Starting backend at: $backendPath" -ForegroundColor Yellow

Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$backendPath'; Write-Host 'Starting PayOps360 Backend...' -ForegroundColor Green; .\mvnw spring-boot:run"

Write-Host "[✓] Backend starting in new window..." -ForegroundColor Green
Write-Host "    URL: http://localhost:8080" -ForegroundColor White
Write-Host "    Swagger: http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host ""

# Wait for backend to start
Write-Host "Waiting for backend to start (30 seconds)..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Starting Frontend Server" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Start frontend in new window
$frontendPath = "C:\Users\sufyan.abdulghani\Downloads\MVP\payOps360\payops360-frontend"
Write-Host "Starting frontend at: $frontendPath" -ForegroundColor Yellow

Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$frontendPath'; Write-Host 'Starting PayOps360 Frontend...' -ForegroundColor Green; npm run dev"

Write-Host "[✓] Frontend starting in new window..." -ForegroundColor Green
Write-Host "    URL: http://localhost:3000" -ForegroundColor White
Write-Host ""

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Application Started!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Access the application:" -ForegroundColor Yellow
Write-Host "  Frontend:  http://localhost:3000" -ForegroundColor White
Write-Host "  Backend:   http://localhost:8080" -ForegroundColor White
Write-Host "  Swagger:   http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host ""
Write-Host "Test Credentials:" -ForegroundColor Yellow
Write-Host "  Email:     admin@payops360.com" -ForegroundColor White
Write-Host "  Password:  Admin@123" -ForegroundColor White
Write-Host ""
Write-Host "Press Ctrl+C to stop this script" -ForegroundColor Gray
Write-Host ""

# Keep script running
while ($true) {
    Start-Sleep -Seconds 10
}

