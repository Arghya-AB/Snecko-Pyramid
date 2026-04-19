SHELL:= /bin/bash
FOLDER = Test3

all : clean compile run

run:
	java -cp .:soot-4.6.0-jar-with-dependencies.jar Main $(FOLDER)

compile:
	javac ./testcases/$(FOLDER)/Test.java
	javac -cp .:soot-4.6.0-jar-with-dependencies.jar Main.java
clean:
	rm -f ./sootOutput/*.class
	rm -f ./sootOutput/*.jimple
	rm -f ./testcases/$(FOLDER)/*.class
	rm -f *.class

test:
	cd ./testcases/$(FOLDER)
	java -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -Xlog:gc Test
	find . -type f -name "*.class" -exec ls -l {} + | awk '{total += $5} END {print total " bytes"}'

