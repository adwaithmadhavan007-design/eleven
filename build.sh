#!/bin/bash
echo "============================================"
echo " MeshChat Build Script"
echo "============================================"

if ! command -v javac &> /dev/null; then
    echo "ERROR: javac not found. Install JDK 17+."
    echo "Ubuntu: sudo apt install openjdk-21-jdk"
    echo "Mac: brew install openjdk@21"
    exit 1
fi

echo "Compiling..."
mkdir -p out

find meshchat -name "*.java" | xargs javac -d out --source-path .

if [ $? -ne 0 ]; then
    echo "BUILD FAILED"
    exit 1
fi

echo ""
echo "BUILD SUCCESSFUL!"
echo "Run with: ./run.sh"
