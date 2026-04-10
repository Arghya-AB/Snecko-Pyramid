SHELL:= /bin/bash
FOLDER = Test2

all : clean compile run

run:
	java -cp .:soot-4.6.0-jar-with-dependencies.jar Main2 $(FOLDER)

compile:
	javac ./testcases/$(FOLDER)/Test.java
	javac -cp .:soot-4.6.0-jar-with-dependencies.jar Main2.java
clean:
	rm -f ./sootOutput/*.jimple
	rm -f ./testcases/$(FOLDER)/*.class
	rm -f *.class
