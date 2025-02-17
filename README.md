HP-Terminal
===========

Description
-----------

A simple implementation of a HP graphics terminal of the 264x or 262x series.

Intended for application with HP 1000 and similar computer systems.

This branch offers the following changes and enhancements:

*  Telnet support
*  Backspace key now deletes the character under the cursor after the cursor has been moved left
*  F10 key toggles visibility of the graphics display which is off by default
*  Fixed position of the cursor rectangle
*  ANT build file for the complete build process
*  Fixed deprecated language elements

Status
------

Needs testing. The Telenet support was only tested superficially on LINUX, Windows 11 and macOS 10.15.

Documentation
-------------

See the file <em>HP Terminal Emulator.pdf</em>.
