JFLAGS = -g
JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
        packet.java \
        sender.java \
        receiver.java 

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
