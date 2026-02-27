@echo off
echo ============================================
echo  MeshChat - Windows Firewall Setup
echo  Run this as ADMINISTRATOR
echo ============================================
echo.

REM Remove old rules first (in case of re-install)
netsh advfirewall firewall delete rule name="MeshChat TCP In" >nul 2>&1
netsh advfirewall firewall delete rule name="MeshChat TCP Out" >nul 2>&1
netsh advfirewall firewall delete rule name="MeshChat UDP In" >nul 2>&1
netsh advfirewall firewall delete rule name="MeshChat UDP Out" >nul 2>&1

echo Adding firewall rules for MeshChat...

netsh advfirewall firewall add rule name="MeshChat TCP In" dir=in action=allow protocol=TCP localport=45678
netsh advfirewall firewall add rule name="MeshChat TCP Out" dir=out action=allow protocol=TCP localport=45678
netsh advfirewall firewall add rule name="MeshChat UDP In" dir=in action=allow protocol=UDP localport=45679
netsh advfirewall firewall add rule name="MeshChat UDP Out" dir=out action=allow protocol=UDP localport=45679

echo.
echo Done! Firewall rules added for:
echo   TCP port 45678 (mesh messages)
echo   UDP port 45679 (peer discovery)
echo.
echo You can now run MeshChat with run.bat
echo.
pause
