@echo off
rem Relance le serveur de dev et les deux clients (LordFinn + Steve2) sur la derniere version du code.
rem Options : relance.bat -Seul (un seul client)   relance.bat -Monde (reconstruit aussi le monde de test)
pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\relance.ps1" %*
if errorlevel 1 pause
