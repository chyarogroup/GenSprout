@echo off
setlocal enabledelayedexpansion

echo ==============================================
echo GenSprout Auto-Publisher (GitHub, Modrinth, Hangar)
echo ==============================================

where node >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo [ERROR] Node.js is not installed or not in PATH!
    exit /b 1
)

node publish.js
if !ERRORLEVEL! neq 0 (
    echo [ERROR] Publish script encountered errors!
    exit /b !ERRORLEVEL!
)

echo ==============================================
echo SUCCESS: Release process finished!
echo ==============================================
