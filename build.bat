@echo off
echo ============================================
echo  MeshChat Build Script for Windows
echo ============================================

REM Check Java
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java not found. Please install JDK 17+ from https://adoptium.net
    pause
    exit /b 1
)

javac -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: javac not found. You need the JDK, not just JRE.
    echo Download JDK 21 from: https://adoptium.net
    pause
    exit /b 1
)

echo Compiling MeshChat...
if not exist out mkdir out

javac -d out --source-path . ^
  meshchat\Main.java ^
  meshchat\model\Message.java ^
  meshchat\model\Peer.java ^
  meshchat\network\MeshNode.java ^
  meshchat\network\PeerConnection.java ^
  meshchat\network\DiscoveryService.java ^
  meshchat\routing\MessageRouter.java ^
  meshchat\ui\ChatWindow.java ^
  meshchat\ui\MessageListener.java ^
  meshchat\util\DeviceIdentity.java ^
  meshchat\util\SimpleJson.java

if errorlevel 1 (
    echo.
    echo BUILD FAILED. Check errors above.
    pause
    exit /b 1
)

echo.
echo BUILD SUCCESSFUL!
echo Run with: run.bat
echo.
pause
