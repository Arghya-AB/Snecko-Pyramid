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


# SHELL:= /bin/bash
# TEST = TestCase1_Branch

# all : clean copy compile run remove

# remove:
# 	rm -f ./$(TEST).java
# copy:
# 	cp ./testcases/$(TEST)/$(TEST).java ./$(TEST).java
# run:
# 	java -cp .:soot-4.6.0-jar-with-dependencies.jar Main $(TEST)

# compile:
# 	javac -cp .:soot-4.6.0-jar-with-dependencies.jar Main.java $(TEST).java
# clean:
# 	rm -f ./sootOutput/*.jimple
# 	rm -f ./testcases/$(TEST)/*.class
# 	rm -f *.class
