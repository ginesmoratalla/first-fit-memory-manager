# first-fit-memory-manager 🗃️

Memory managment simulator in Java. Project for **Operating Systems** module (2nd year Computer Science).

This memory management structure will include a simulation of:
- **malloc**
- **free**
- **sbrk**

## Project Goals
- Implement a concurrent programming approach for handling tasks performed by a robot.
- Understand how multithreadding works and apply it to a Java program.
- Identify bottleneck found in one of the robot components

## How to run
_build_ and _run_ shell scripts are provided to compile and run the project.

1. cd into directory
2. `./build.sh`
3. `./run.sh`
  
## Overview

This memory allocator system follows the following structure:

![0aa286bdaf2f927587daa405c342bafa](https://github.com/ginesmoratalla/first-fit-memory-manager/assets/126341997/22ab6d46-052b-4c1d-b24e-432ff0b9b39d)

### 1. malloc()
Request a chunk of memory of desired size (in bytes). will allocate a slightly bigger size for the chunk's metadata

### 2. free()
Free a 
