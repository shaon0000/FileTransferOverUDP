#!/bin/bash
HOST1=ubuntu1204-002.student.cs.uwaterloo.ca
HOST2=ubuntu1204-006.student.cs.uwaterloo.ca
HOST3=ubuntu1204-004.student.cs.uwaterloo.ca

if [ "$1" = "nem" ]
then
    echo Firing up the emulator
    ./nEmulator 9991 $HOST2 9994 9993 $HOST3 9992 1 0.2 1
fi

if [ "$1" = "recv" ]
then
    echo Firing up receiver
    java Receiver $HOST1 9993 9994 copy.txt
fi

if [ "$1" = "send" ]
then
    echo Firing up sender
    java Sender $HOST1 9991 9992 sample.txt
fi
