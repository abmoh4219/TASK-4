@REM RegistrarOps Maven wrapper for Windows
@echo off
where mvn >NUL 2>&1
if %ERRORLEVEL% EQU 0 (
  mvn %*
  exit /b %ERRORLEVEL%
)
echo Please install Apache Maven 3.9+ and ensure 'mvn' is on PATH.
exit /b 1
