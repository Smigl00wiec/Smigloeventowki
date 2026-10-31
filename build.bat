@echo off
cd /d "%~dp0"
where mvn >nul 2>&1 || (echo Nie znaleziono Mavena. Zainstaluj JDK 21 i Maven, potem uruchom ponownie. & pause & exit /b 1)
call mvn -B package
if errorlevel 1 (echo BLAD BUDOWANIA & pause & exit /b 1)
copy /Y target\Smigloeventowki.jar "%USERPROFILE%\Desktop\Smigloeventowki.jar"
echo Gotowe! Plik jest na pulpicie.
pause
