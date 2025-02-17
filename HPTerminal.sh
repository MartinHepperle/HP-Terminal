#$!/bin/sh
# ---------------------
# HP Terminal Emulator
# Martin Hepperle, 12/2019
# ---------------------
#
# Example for Linux e.g. on Raspberry Pi
# java -jar HPTerminal.jar -sound 0
# java -jar HPTerminal.jar -sound 0 -debug 1 -logging 1
# java -jar HPTerminal.jar -port \\.\COM25 -speed 4800
#
# Note that line endings of this script must be a single LF
# as Linux shells are still unable to cope with CR+LF and 
# output nonsensical error messages.
#
java -jar HPTerminal.jar -port /dev/ttyAMA0 -sound 0 -ANSI 0
#