JV=javac

sc = $(wildcard *.java)
cs = $(sc:.java=.class)

all: $(cs)

clean : 
	rm -f *.class

%.class : %.java
	$(JV) $<
