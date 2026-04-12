SHELL:= /bin/bash

all : clean compile run

run:
	java -cp .:soot-4.6.0-jar-with-dependencies.jar MainClass .

compile:
	javac ./Test.java
	javac -cp .:soot-4.6.0-jar-with-dependencies.jar MainClass.java
clean:
	rm -f ./sootOutput/*.jimple
	rm -f ./*.class
