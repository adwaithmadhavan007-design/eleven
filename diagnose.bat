@echo off
echo ============================================
echo  MeshChat Network Diagnostics
echo ============================================
echo.

echo [1] Your local IP addresses:
ipconfig | findstr /i "IPv4"
echo.

echo [2] Checking if Java is available:
java -version
echo.

echo [3] Checking port 45678 (TCP mesh):
netstat -an | findstr ":45678"
echo.

echo [4] Checking port 45679 (UDP discovery):
netstat -an | findstr ":45679"
echo.

echo [5] Firewall rules for MeshChat:
netsh advfirewall firewall show rule name="MeshChat TCP In" 2>nul
netsh advfirewall firewall show rule name="MeshChat UDP In" 2>nul
echo.

echo ============================================
echo  INSTRUCTIONS:
echo  1. Share your IPv4 address (shown above) with other users
echo  2. They can type it in the '+ Connect by IP' button
echo  3. If ports show as LISTENING, MeshChat is running correctly
echo  4. Run setup_firewall.bat as Admin if firewall rules are missing
echo ============================================
pause
