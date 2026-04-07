#!/bin/bash
set -e

CLASSPATH="soot-4.6.0-jar-with-dependencies.jar:."

echo "Compiling"
javac -cp $CLASSPATH Main.java SampleSceneTransform.java TestCase1_Branch.java
echo "Running TestCase1_Branch"
java -cp $CLASSPATH Main TestCase1_Branch TestCase1_Branch_Node

echo "Script Finished!"